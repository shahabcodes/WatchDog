package com.shahaabapps.core.firebase.e2ee

import android.content.Context
import android.util.Base64
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.crypto.DeviceKeyStore
import com.shahaabapps.core.common.crypto.E2ee
import com.shahaabapps.core.common.crypto.PinnedKeyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ties the device identity ([DeviceKeyStore]) to the backend key exchange
 * ([E2eeKeyRepository]) and holds the child's data key.
 *
 * Child: owns a random data key (persisted sealed under the hardware master key)
 * and wraps it for each linked parent. Parent: unwraps its copy of a child's
 * data key to decrypt that child's payloads. The private key and the plaintext
 * data key never leave the device.
 */
@Singleton
class E2eeManager @Inject constructor(
    @ApplicationContext context: Context,
    private val deviceKeyStore: DeviceKeyStore,
    private val keys: E2eeKeyRepository,
    private val pinnedKeys: PinnedKeyStore,
    private val diagnostics: com.shahaabapps.core.firebase.diagnostics.DiagnosticsLogger,
) {
    private val appContext = context.applicationContext
    private val dekLock = Any()
    /** This child's key-ring, newest first. ring[0] encrypts; any entry can decrypt. */
    @Volatile private var cachedRing: List<ByteArray>? = null
    /** Last linked-parent set seen, to detect a removal and rotate. */
    @Volatile private var lastLinkedParents: Set<String>? = null

    /**
     * True once every currently-linked parent has a wrapped copy of the data key,
     * i.e. the child may safely encrypt (no parent would be locked out). Starts
     * false, so the child uploads plaintext until key exchange completes — which
     * makes the cutover to encryption automatic and non-breaking.
     */
    @Volatile var childEncryptionReady: Boolean = false
        private set

    /** Parent-side cache of unwrapped key-rings, keyed by childId. */
    private val parentRingCache = java.util.concurrent.ConcurrentHashMap<String, List<ByteArray>>()

    /**
     * "childId/parentUid" -> last time (ms) we confirmed a wrapped key sealed for
     * the parent's *current* device key. Lets routine syncs short-circuit, while
     * re-verifying every [VERIFY_TTL_MS] so a parent that reinstalled with a new
     * device key is re-wrapped within that window even if the child never restarts.
     */
    private val verifiedParents = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile private var childKeyPublished = false

    fun myPublicKeyB64(): String = b64(deviceKeyStore.publicKey())
    fun myFingerprint(): String = deviceKeyStore.publicKeyFingerprint()

    // ---- child side ----

    private fun dekFile() = File(File(appContext.filesDir, "e2ee"), "dek.key")

    /** The child's key-ring (newest first), generated and persisted (sealed) on first use. */
    fun childKeyring(): List<ByteArray> {
        cachedRing?.let { return it }
        synchronized(dekLock) {
            cachedRing?.let { return it }
            val file = dekFile()
            val ring = if (file.exists()) {
                runCatching { decodeRing(deviceKeyStore.openLocal(file.readBytes())) }.getOrNull()
            } else null
            val finalRing = ring?.takeIf { it.isNotEmpty() } ?: listOf(E2ee.newDataKey()).also { persistRing(it) }
            cachedRing = finalRing
            return finalRing
        }
    }

    /** The current encryption key (newest in the ring). */
    fun childDataKey(): ByteArray = childKeyring().first()

    /** Add a fresh key to the front of the ring (used when a parent is removed). */
    private fun rotateKeyring() {
        synchronized(dekLock) {
            val rotated = listOf(E2ee.newDataKey()) + childKeyring()
            persistRing(rotated)
            cachedRing = rotated
        }
    }

    private fun persistRing(ring: List<ByteArray>) {
        val file = dekFile()
        file.parentFile?.mkdirs()
        file.writeBytes(deviceKeyStore.sealLocal(encodeRing(ring)))
    }

    private fun encodeRing(ring: List<ByteArray>): ByteArray {
        val out = ByteArray(1 + ring.size * 32)
        out[0] = ring.size.toByte()
        ring.forEachIndexed { i, k -> System.arraycopy(k, 0, out, 1 + i * 32, 32) }
        return out
    }

    private fun decodeRing(bytes: ByteArray): List<ByteArray> {
        // Legacy single-key file (pre-rotation) is a bare 32-byte key.
        if (bytes.size == 32) return listOf(bytes)
        val count = bytes[0].toInt() and 0xFF
        return (0 until count).map { bytes.copyOfRange(1 + it * 32, 1 + (it + 1) * 32) }
    }

    /** Try every key in [ring] (newest first); return the first that authenticates. */
    private fun tryOpen(ring: List<ByteArray>?, blob: ByteArray): ByteArray? {
        ring ?: return null
        for (key in ring) runCatching { return E2ee.openPayload(key, blob) }
        return null
    }

    private fun readerRing(childId: String, viewerUid: String?): List<ByteArray>? =
        if (viewerUid != null && viewerUid == childId) cachedRing else parentRingCache[childId]

    /**
     * Publish the child public key and ensure every linked parent has a wrapped
     * copy of the data key. Safe to call repeatedly; a parent whose public key
     * isn't published yet is simply retried on the next call.
     */
    suspend fun syncChildKeys(childId: String, linkedParents: List<String>) =
        withContext(Dispatchers.Default) {
            // EC key-gen / ECDH / file I/O — keep off the main thread. Safe to call
            // often (e.g. on every child snapshot): publishing is done once and
            // already-wrapped parents short-circuit, so a parent that publishes its
            // key late is still picked up on a later call without write amplification.
            if (!childKeyPublished &&
                keys.publishChildPublicKey(childId, myPublicKeyB64()) is AppResult.Success
            ) {
                childKeyPublished = true
            }
            // If a parent was removed since the last sync, rotate the key-ring so
            // their cached key can't read future data (forward secrecy), delete
            // their wrapped key, and force a re-wrap for the remaining parents.
            val current = linkedParents.toSet()
            val removed = lastLinkedParents?.minus(current).orEmpty()
            if (removed.isNotEmpty()) {
                rotateKeyring()
                verifiedParents.clear()
                for (gone in removed) keys.deleteWrappedKey(childId, gone)
            }
            lastLinkedParents = current
            var allWrapped = linkedParents.isNotEmpty()
            for (parentUid in linkedParents) {
                if (!ensureWrappedFor(childId, parentUid)) allWrapped = false
            }
            childEncryptionReady = allWrapped
        }

    /**
     * The data key to encrypt with, or null if it isn't safe to encrypt yet.
     *
     * Normally gated on [childEncryptionReady] so a capture is never encrypted
     * before every linked parent can read it (the parent would otherwise see an
     * undecryptable blob). Once the fleet has finished key exchange — verified on
     * real devices — flipping [ENCRYPT_ONLY] to true removes the plaintext-write
     * fallback entirely (the P6 cutover): writers then always encrypt.
     *
     * Read paths intentionally stay dual-read regardless, so plaintext history
     * written before the cutover remains viewable ("encrypt new data only").
     */
    fun encryptionKeyIfReady(): ByteArray? =
        if (ENCRYPT_ONLY || childEncryptionReady) childDataKey() else null

    /**
     * Child: seal a coordinate pair into a base64 blob, or null if it isn't safe
     * to encrypt yet (caller then stores plaintext). Coordinates pack to 16 bytes.
     */
    fun sealCoords(lat: Double, lng: Double): String? {
        val key = encryptionKeyIfReady() ?: return null
        val packed = java.nio.ByteBuffer.allocate(16).putDouble(lat).putDouble(lng).array()
        return b64(E2ee.sealPayload(key, packed))
    }

    /** Child: seal raw bytes (e.g. a screenshot) under the current key, or null if not ready. */
    fun sealBytesIfReady(bytes: ByteArray): ByteArray? =
        encryptionKeyIfReady()?.let { E2ee.sealPayload(it, bytes) }

    /** Parent: decrypt raw bytes, warming + trying the child's key-ring; null if unavailable. */
    suspend fun openBytes(childId: String, viewerUid: String?, blob: ByteArray): ByteArray? {
        warmReaderKey(childId, viewerUid)
        return tryOpen(readerRing(childId, viewerUid), blob).also {
            if (it == null) diagnostics.info("e2ee", "bytes decrypt miss (no key) child=$childId viewer=$viewerUid")
        }
    }

    /** Parent: decrypt a coordinate blob using the already-cached key-ring; null if unavailable. */
    fun openCoordsCached(childId: String, sealedB64: String): Pair<Double, Double>? {
        val raw = tryOpen(parentRingCache[childId], unb64(sealedB64)) ?: return null
        return runCatching {
            val bb = java.nio.ByteBuffer.wrap(raw)
            bb.double to bb.double
        }.getOrNull()
    }

    /** Parent: ensure this child's key-ring is loaded/cached (call before reading encrypted docs). */
    suspend fun warmDataKey(childId: String, parentUid: String) {
        dataKeyForChild(childId, parentUid, null)
    }

    // ---- generic reader/writer-aware payloads (for data both sides read, e.g. geofences) ----

    /**
     * Seal an arbitrary string for [childId]'s data. [writerUid] picks the key:
     * the child uses its own (ready-gated) data key; a parent uses its unwrapped
     * copy. Returns null when encryption isn't possible yet (store plaintext).
     */
    suspend fun sealString(childId: String, writerUid: String?, plaintext: String): String? =
        withContext(Dispatchers.Default) {
            // Encrypt with the newest key (ring[0]); the child uses its ready-gated
            // key, a parent its unwrapped ring's newest entry.
            val key: ByteArray? = if (writerUid == childId) encryptionKeyIfReady()
            else writerUid?.let { dataKeyForChild(childId, it, null)?.firstOrNull() }
            key?.let { b64(E2ee.sealPayload(it, plaintext.toByteArray(Charsets.UTF_8))) }
        }

    /** Ensure the key-ring for reading [childId] is cached, whichever side this device is. */
    suspend fun warmReaderKey(childId: String, viewerUid: String?) {
        withContext(Dispatchers.Default) {
            if (viewerUid != null && viewerUid == childId) childKeyring()
            else viewerUid?.let { dataKeyForChild(childId, it, null) }
        }
    }

    /** Decrypt a string sealed for [childId], using the already-cached key-ring; null if unavailable. */
    fun openStringCached(childId: String, viewerUid: String?, sealedB64: String): String? {
        val raw = tryOpen(readerRing(childId, viewerUid), unb64(sealedB64))
        if (raw == null) {
            diagnostics.info("e2ee", "string decrypt miss (no key) child=$childId viewer=$viewerUid")
            return null
        }
        return runCatching { String(raw, Charsets.UTF_8) }.getOrNull()
    }

    private suspend fun ensureWrappedFor(childId: String, parentUid: String): Boolean {
        val tag = "$childId/$parentUid"
        val now = System.currentTimeMillis()
        val lastVerified = verifiedParents[tag]
        if (lastVerified != null && now - lastVerified < VERIFY_TTL_MS) return true
        val parentPubB64 = keys.getParentPublicKey(parentUid) ?: return false
        val parentFp = E2ee.fingerprint(unb64(parentPubB64))
        val ringBytes = encodeRing(childKeyring())
        val ringFp = E2ee.fingerprint(ringBytes)
        // Re-wrap when the existing wrap is missing, was sealed for a different
        // parent device key (parent reinstalled), or predates a key rotation.
        val existing = keys.getWrappedKey(childId, parentUid)
        if (existing == null || existing.parentKeyFp != parentFp || existing.ringFp != ringFp) {
            val wrapped = deviceKeyStore.wrapDataKeyFor(unb64(parentPubB64), ringBytes)
            keys.putWrappedKey(childId, parentUid, b64(wrapped), ringFp, parentFp)
        }
        verifiedParents[tag] = now
        return true
    }

    // ---- parent side ----

    /** Publish this parent's public key so children can wrap their data key for it. */
    suspend fun publishParentKey(parentUid: String) = withContext(Dispatchers.Default) {
        keys.publishParentPublicKey(parentUid, myPublicKeyB64())
        Unit
    }

    /** Pin a child's public key captured out-of-band from its pairing QR. */
    suspend fun pinChildKey(childId: String, childPublicKeyB64: String) {
        pinnedKeys.pin(childId, childPublicKeyB64)
        parentRingCache.remove(childId) // re-derive against the verified key next read
    }

    /** True once this child's key has been verified out-of-band (via a QR scan). */
    fun observeChildKeyVerified(childId: String) = pinnedKeys.observePinnedKey(childId)

    /**
     * Unwrap this parent's copy of [childId]'s key-ring, or null if it hasn't been
     * wrapped yet (e.g. the child hasn't come online since pairing).
     * [childPublicKeyB64] should come from the pairing QR when available (most
     * trustworthy), else the published child key.
     */
    suspend fun dataKeyForChild(
        childId: String,
        parentUid: String,
        childPublicKeyB64: String?,
    ): List<ByteArray>? {
        parentRingCache[childId]?.let { return it }
        return withContext(Dispatchers.Default) {
            val wrapped = keys.getWrappedKey(childId, parentUid) ?: return@withContext null
            // Prefer the out-of-band pinned key (verified via QR); fall back to the
            // caller-supplied key, then the backend-published key (trust-on-first-use).
            val childPub = pinnedKeys.getPinnedKey(childId)
                ?: childPublicKeyB64
                ?: keys.getChildPublicKey(childId)
                ?: return@withContext null
            runCatching {
                decodeRing(deviceKeyStore.unwrapDataKeyFrom(unb64(childPub), unb64(wrapped.wrappedB64)))
            }.getOrNull()?.also { parentRingCache[childId] = it }
        }
    }

    /**
     * Drop in-memory session key material on sign-out, so a different account
     * signing in on the same device starts clean. The device identity keypair
     * (per-device, not per-account) is intentionally kept.
     */
    fun clearSession() {
        parentRingCache.clear()
        verifiedParents.clear()
        lastLinkedParents = null
        childKeyPublished = false
        childEncryptionReady = false
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        /**
         * P6 encrypt-only cutover. Keep false until every active device has
         * completed key exchange (verified on real hardware); flipping it true
         * makes child writers stop emitting plaintext. One-way for new data — do
         * not flip without that verification.
         */
        const val ENCRYPT_ONLY = false

        /** How often the child re-confirms each parent's wrapped key is current. */
        private const val VERIFY_TTL_MS = 30L * 60 * 1000
    }
}
