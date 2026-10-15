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
) {
    private val appContext = context.applicationContext
    private val dekLock = Any()
    @Volatile private var cachedDek: ByteArray? = null

    /**
     * True once every currently-linked parent has a wrapped copy of the data key,
     * i.e. the child may safely encrypt (no parent would be locked out). Starts
     * false, so the child uploads plaintext until key exchange completes — which
     * makes the cutover to encryption automatic and non-breaking.
     */
    @Volatile var childEncryptionReady: Boolean = false
        private set

    /** Parent-side cache of unwrapped data keys, keyed by childId. */
    private val parentDekCache = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

    /** "childId/parentUid" pairs already wrapped, so repeated syncs are near-free. */
    private val wrappedParents = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile private var childKeyPublished = false

    fun myPublicKeyB64(): String = b64(deviceKeyStore.publicKey())
    fun myFingerprint(): String = deviceKeyStore.publicKeyFingerprint()

    // ---- child side ----

    /** The child's data key, generated and persisted (sealed) on first use. */
    fun childDataKey(): ByteArray {
        cachedDek?.let { return it }
        synchronized(dekLock) {
            cachedDek?.let { return it }
            val file = File(File(appContext.filesDir, "e2ee"), "dek.key")
            val existing = if (file.exists()) {
                runCatching { deviceKeyStore.openLocal(file.readBytes()) }.getOrNull()
            } else null
            val dek = existing ?: E2ee.newDataKey().also { fresh ->
                file.parentFile?.mkdirs()
                file.writeBytes(deviceKeyStore.sealLocal(fresh))
            }
            cachedDek = dek
            return dek
        }
    }

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

    /** Parent: decrypt a coordinate blob using an already-cached data key; null if unavailable. */
    fun openCoordsCached(childId: String, sealedB64: String): Pair<Double, Double>? =
        parentDekCache[childId]?.let { decodeCoords(it, sealedB64) }

    /** Parent: ensure this child's data key is loaded/cached (call before reading encrypted docs). */
    suspend fun warmDataKey(childId: String, parentUid: String): ByteArray? =
        dataKeyForChild(childId, parentUid, null)

    private fun decodeCoords(key: ByteArray, sealedB64: String): Pair<Double, Double>? = runCatching {
        val raw = E2ee.openPayload(key, unb64(sealedB64))
        val bb = java.nio.ByteBuffer.wrap(raw)
        bb.double to bb.double
    }.getOrNull()

    // ---- generic reader/writer-aware payloads (for data both sides read, e.g. geofences) ----

    /**
     * Seal an arbitrary string for [childId]'s data. [writerUid] picks the key:
     * the child uses its own (ready-gated) data key; a parent uses its unwrapped
     * copy. Returns null when encryption isn't possible yet (store plaintext).
     */
    suspend fun sealString(childId: String, writerUid: String?, plaintext: String): String? =
        withContext(Dispatchers.Default) {
            val key = if (writerUid == childId) encryptionKeyIfReady()
            else writerUid?.let { dataKeyForChild(childId, it, null) }
            key?.let { b64(E2ee.sealPayload(it, plaintext.toByteArray(Charsets.UTF_8))) }
        }

    /** Ensure the data key for reading [childId] is cached, whichever side this device is. */
    suspend fun warmReaderKey(childId: String, viewerUid: String?): ByteArray? =
        withContext(Dispatchers.Default) {
            if (viewerUid != null && viewerUid == childId) childDataKey()
            else viewerUid?.let { dataKeyForChild(childId, it, null) }
        }

    /** Decrypt a string sealed for [childId], using the already-cached data key; null if unavailable. */
    fun openStringCached(childId: String, viewerUid: String?, sealedB64: String): String? {
        val key = if (viewerUid != null && viewerUid == childId) cachedDek else parentDekCache[childId]
        return key?.let {
            runCatching { String(E2ee.openPayload(it, unb64(sealedB64)), Charsets.UTF_8) }.getOrNull()
        }
    }

    private suspend fun ensureWrappedFor(childId: String, parentUid: String): Boolean {
        val tag = "$childId/$parentUid"
        if (tag in wrappedParents) return true
        if (keys.hasWrappedKey(childId, parentUid)) { wrappedParents.add(tag); return true }
        val parentPubB64 = keys.getParentPublicKey(parentUid) ?: return false
        val wrapped = deviceKeyStore.wrapDataKeyFor(unb64(parentPubB64), childDataKey())
        keys.putWrappedKey(childId, parentUid, b64(wrapped), myFingerprint())
        wrappedParents.add(tag)
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
        parentDekCache.remove(childId) // re-derive against the verified key next read
    }

    /** True once this child's key has been verified out-of-band (via a QR scan). */
    fun observeChildKeyVerified(childId: String) = pinnedKeys.observePinnedKey(childId)

    /**
     * Unwrap this parent's copy of [childId]'s data key, or null if it hasn't
     * been wrapped yet (e.g. the child hasn't come online since pairing).
     * [childPublicKeyB64] should come from the pairing QR when available (most
     * trustworthy), else the published child key.
     */
    suspend fun dataKeyForChild(
        childId: String,
        parentUid: String,
        childPublicKeyB64: String?,
    ): ByteArray? {
        parentDekCache[childId]?.let { return it }
        return withContext(Dispatchers.Default) {
            val wrapped = keys.getWrappedKey(childId, parentUid) ?: return@withContext null
            // Prefer the out-of-band pinned key (verified via QR); fall back to the
            // caller-supplied key, then the backend-published key (trust-on-first-use).
            val childPub = pinnedKeys.getPinnedKey(childId)
                ?: childPublicKeyB64
                ?: keys.getChildPublicKey(childId)
                ?: return@withContext null
            runCatching {
                deviceKeyStore.unwrapDataKeyFrom(unb64(childPub), unb64(wrapped.wrappedB64))
            }.getOrNull()?.also { parentDekCache[childId] = it }
        }
    }

    /**
     * Drop in-memory session key material on sign-out, so a different account
     * signing in on the same device starts clean. The device identity keypair
     * (per-device, not per-account) is intentionally kept.
     */
    fun clearSession() {
        parentDekCache.clear()
        wrappedParents.clear()
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
    }
}
