package com.shahaabapps.core.firebase.e2ee

import android.content.Context
import android.util.Base64
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.crypto.DeviceKeyStore
import com.shahaabapps.core.common.crypto.E2ee
import com.shahaabapps.core.common.crypto.PinnedKeyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Throttles parent-side re-sync requests per child so a read burst sends one. */
    private val lastResyncRequest = ConcurrentHashMap<String, Long>()
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

    /** The parent account reading on this phone (for re-fetching a key-ring after a miss). */
    @Volatile private var parentViewerUid: String? = null

    /** Parent-side cache of unwrapped key-rings, keyed by childId. */
    private val parentRingCache = java.util.concurrent.ConcurrentHashMap<String, List<ByteArray>>()

    /**
     * "childId/parentUid" -> last time (ms) we confirmed a wrapped key sealed for
     * the parent's *current* device key. Lets routine syncs short-circuit, while
     * re-verifying every [VERIFY_TTL_MS] so a parent that reinstalled with a new
     * device key is re-wrapped within that window even if the child never restarts.
     */
    private val verifiedParents = java.util.concurrent.ConcurrentHashMap<String, Long>()
    /**
     * Parents with no usable key at all (signed out of every phone). They can't read
     * anything, so they don't hold back encryption; when they sign in again, a fresh
     * wrap of the whole key-ring lets them read what was written meanwhile.
     */
    private val signedOutParents = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    /** Whether a parent had no phones listed at the last check. */
    private val noDevices = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    @Volatile private var childKeyPublished = false

    // ---- child side: pinned parent keys ----
    // Shield remembers each parent's public-key fingerprint (pinned when the child
    // approves the parent, or on first sight for parents linked before pinning).
    // If a parent's key later changes — a new phone, or someone with a stolen
    // parent login swapping it — Shield stops sharing its data key with that
    // parent until the child's phone approves the change.
    private val parentPins = appContext.getSharedPreferences(PARENT_PINS_PREFS, Context.MODE_PRIVATE)
    private val _parentKeyChanges = kotlinx.coroutines.flow.MutableStateFlow(loadPendingChanges())

    /** Parents whose key changed and awaits approval on this phone: uid -> new fingerprint. */
    val parentKeyChanges: kotlinx.coroutines.flow.StateFlow<Map<String, String>> = _parentKeyChanges

    private fun loadPendingChanges(): Map<String, String> =
        parentPins.all.mapNotNull { (k, v) ->
            if (k.startsWith(PENDING_PREFIX) && v is String) k.removePrefix(PENDING_PREFIX) to v else null
        }.toMap()

    /** The email shown when this parent was approved ("" if pinned on first sight). */
    fun parentEmail(parentUid: String): String =
        parentPins.getString(EMAIL_PREFIX + parentUid.substringBefore('/'), "") ?: ""

    /** The phone model of a pending device key ("" for the legacy single key). */
    fun pendingDeviceModel(changeKey: String): String = parentPins.getString(MODEL_PREFIX + changeKey, "") ?: ""

    /** Pin [parentUid]'s current key now (called when the child approves the parent). */
    suspend fun pinParentKey(parentUid: String, email: String) = withContext(Dispatchers.Default) {
        val edit = parentPins.edit().putString(EMAIL_PREFIX + parentUid, email)
        keys.getParentPublicKey(parentUid)?.let {
            edit.putString(PIN_PREFIX + parentUid, E2ee.fingerprint(unb64(it))).remove(PENDING_PREFIX + parentUid)
        }
        // Every device the parent has signed in on so far is approved with them.
        keys.listParentDevicesFresh(parentUid)?.forEach { d ->
            val key = "$parentUid/${d.deviceId}"
            edit.putString(PIN_PREFIX + key, E2ee.fingerprint(unb64(d.publicKeyB64))).remove(PENDING_PREFIX + key)
        }
        edit.remove(REVOKED_PREFIX + parentUid).apply()
        _parentKeyChanges.value = _parentKeyChanges.value.filterKeys { it.substringBefore('/') != parentUid }
    }

    /** The child's phone approved [parentUid]'s new key: pin it and share again. */
    fun approveParentKeyChange(changeKey: String) {
        val fp = _parentKeyChanges.value[changeKey] ?: return
        parentPins.edit().putString(PIN_PREFIX + changeKey, fp).remove(PENDING_PREFIX + changeKey)
            .remove(MODEL_PREFIX + changeKey).apply()
        _parentKeyChanges.value = _parentKeyChanges.value - changeKey
        val uid = changeKey.substringBefore('/')
        revokedFps(uid).let { if (fp in it) setRevoked(uid, it - fp) }
        verifiedParents.keys.removeAll { it.endsWith("/$uid") }
    }

    /** Keys of [parentUid]'s phones that signed out; they need approving again. */
    private fun revokedFps(parentUid: String): Set<String> =
        parentPins.getStringSet(REVOKED_PREFIX + parentUid, null)?.toSet().orEmpty()

    private fun setRevoked(parentUid: String, fps: Set<String>) {
        val edit = parentPins.edit()
        if (fps.isEmpty()) edit.remove(REVOKED_PREFIX + parentUid) else edit.putStringSet(REVOKED_PREFIX + parentUid, fps)
        edit.apply()
    }

    /** Keys of [parentUid]'s phones this phone has approved. */
    private fun approvedDeviceFps(parentUid: String): Set<String> =
        parentPins.all.mapNotNullTo(HashSet()) { (k, v) -> if (k.startsWith("$PIN_PREFIX$parentUid/")) v as? String else null }

    private fun markPending(changeKey: String, fp: String, model: String = "") {
        if (_parentKeyChanges.value[changeKey] == fp) return
        parentPins.edit().putString(PENDING_PREFIX + changeKey, fp).putString(MODEL_PREFIX + changeKey, model).apply()
        _parentKeyChanges.value = _parentKeyChanges.value + (changeKey to fp)
        diagnostics.warn("e2ee", "parent key needs approval key=$changeKey")
    }

    private fun clearPending(changeKey: String) {
        if (changeKey !in _parentKeyChanges.value && !parentPins.contains(PENDING_PREFIX + changeKey)) return
        parentPins.edit().remove(PENDING_PREFIX + changeKey).remove(MODEL_PREFIX + changeKey).apply()
        _parentKeyChanges.value = _parentKeyChanges.value - changeKey
    }

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

    /** Syncs left in which to look for a ring handed over after a restore. */
    @Volatile private var importChecksLeft = 30

    /**
     * Child, after a restore: merge the old key-ring a linked parent handed over
     * (wrapped for this install's key), so the old data stays readable. Only from a
     * linked parent, sent from one of that parent's published keys. The ring keeps
     * this install's newest key first; old keys are added after it.
     */
    private suspend fun importRestoredRings(childId: String, linkedParents: Set<String>) {
        if (importChecksLeft <= 0) return
        importChecksLeft--
        val imports = keys.listRingImports(childId)
        if (imports.isEmpty()) return
        for (imp in imports) {
            val trusted = imp.parentUid in linkedParents && run {
                val fp = E2ee.fingerprint(unb64(imp.senderPubB64))
                val known = buildSet {
                    keys.readParentKeyFresh(imp.parentUid).getOrNull()?.let { add(E2ee.fingerprint(unb64(it))) }
                    keys.listParentDevicesFresh(imp.parentUid)?.forEach { add(E2ee.fingerprint(unb64(it.publicKeyB64))) }
                }
                fp in known
            }
            val ring = if (trusted) {
                runCatching { decodeRing(deviceKeyStore.unwrapDataKeyFrom(unb64(imp.senderPubB64), unb64(imp.wrappedB64))) }.getOrNull()
            } else null
            if (ring != null) {
                synchronized(dekLock) {
                    val cur = childKeyring()
                    val add = ring.filter { k -> cur.none { it.contentEquals(k) } }
                    if (add.isNotEmpty()) {
                        val merged = (cur + add).take(MAX_RING)
                        persistRing(merged)
                        cachedRing = merged
                    }
                }
                verifiedParents.clear()
                diagnostics.info("e2ee", "restored key-ring merged from parent=${imp.parentUid} keys=${ring.size}")
            } else {
                diagnostics.warn("e2ee", "ignored a handed-over key-ring parent=${imp.parentUid}")
            }
            keys.deleteRingImport(childId, imp.parentUid)
        }
    }

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

    /**
     * Try every key in [ring] (newest first); return the first that authenticates.
     * Tries with [aad] bound first, then without — so payloads sealed by an older
     * build (no AAD) still open while new payloads are context-bound.
     */
    private fun tryOpen(ring: List<ByteArray>?, blob: ByteArray, aad: ByteArray? = null): ByteArray? {
        ring ?: return null
        for (key in ring) {
            runCatching { return E2ee.openPayload(key, blob, aad) }
            if (aad != null) runCatching { return E2ee.openPayload(key, blob, null) }
        }
        return null
    }

    /** Associated data binding a payload to its child, so it can't be pasted cross-child. */
    private fun aadFor(childId: String): ByteArray = childId.toByteArray(Charsets.UTF_8)

    /**
     * Per-document binding ("slot", e.g. "geofences/<id>"): a payload sealed for one
     * document won't open in another, so a writer can't paste one encrypted value
     * into a different document. Older payloads (child-only or no AAD) still open.
     */
    private fun aadFor(childId: String, slot: String?): ByteArray =
        if (slot == null) aadFor(childId) else "$childId|$slot".toByteArray(Charsets.UTF_8)

    /** Opens with the slot binding first, then the older child-only binding (and none). */
    private fun tryOpenSlot(ring: List<ByteArray>?, blob: ByteArray, childId: String, slot: String?): ByteArray? {
        if (slot != null) {
            ring ?: return null
            for (key in ring) runCatching { return E2ee.openPayload(key, blob, aadFor(childId, slot)) }
        }
        return tryOpen(ring, blob, aadFor(childId))
    }

    // Writers only switch to slot binding once the readers on the other side can
    // open it — so a phone that updates later never sees undecryptable data.
    /** Parent side: children whose Shield reads slot-bound payloads. */
    private val childReadsSlots = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    /** Child side: linked parents whose Watch Dog reads slot-bound payloads. */
    private val parentReadsSlots = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    @Volatile private var currentParents: Set<String> = emptySet()

    /** Parent side: record a child's capability (from its plaintext status flags). */
    fun noteChildReadsSlots(childId: String, reads: Boolean) { childReadsSlots[childId] = reads }

    private fun childWritesSlots(): Boolean {
        val parents = currentParents
        return parents.isNotEmpty() && parents.all { parentReadsSlots[it] == true }
    }

    /** The binding a writer uses now: slot-bound once the readers support it. */
    private fun writerAad(childId: String, writerIsChild: Boolean, slot: String?): ByteArray {
        val v2 = slot != null && if (writerIsChild) childWritesSlots() else childReadsSlots[childId] == true
        return if (v2) aadFor(childId, slot) else aadFor(childId)
    }

    /**
     * Per-child "encryption cutover" high-water mark. Set once the one-time
     * migration has re-encrypted all pre-E2EE plaintext for a child (see
     * [E2eeMigrator]); from then on readers MUST refuse to fall back to a
     * plaintext field for that child, since a plaintext value could only be a
     * writer/backend downgrade or forgery. Persisted so the guard survives
     * restarts. Not set before migration completes, so legitimate plaintext
     * history is never hidden or lost.
     */
    private val cutover = ConcurrentHashMap<String, Boolean>()
    private fun cutoverPrefs() = appContext.getSharedPreferences("e2ee_cutover", Context.MODE_PRIVATE)
    fun encryptionActive(childId: String): Boolean =
        cutover[childId] ?: cutoverPrefs().getBoolean(childId, false).also { cutover[childId] = it }

    /** Mark the encryption cutover for [childId] (called by the migrator when done). */
    fun markEncryptionCutover(childId: String) {
        cutover[childId] = true
        runCatching { cutoverPrefs().edit().putBoolean(childId, true).apply() }
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
                for (gone in removed) {
                    keys.deleteWrappedKey(childId, gone)
                    keys.listDeviceWrapIds(childId, gone).forEach { keys.deleteDeviceWrap(childId, gone, it) }
                    val edit = parentPins.edit()
                    parentPins.all.keys
                        .filter { k -> listOf(PIN_PREFIX, PENDING_PREFIX, EMAIL_PREFIX, MODEL_PREFIX, REVOKED_PREFIX).any { pfx -> k == pfx + gone || k.startsWith("$pfx$gone/") } }
                        .forEach { edit.remove(it) }
                    edit.apply()
                    _parentKeyChanges.value = _parentKeyChanges.value.filterKeys { it.substringBefore('/') != gone }
                }
            }
            lastLinkedParents = current
            currentParents = current
            importRestoredRings(childId, current)
            var allWrapped = linkedParents.isNotEmpty()
            for (parentUid in linkedParents) {
                val wrapped = ensureWrappedFor(childId, parentUid)
                // A parent whose key changed is held back until approved, but must
                // not make the child fall back to unencrypted uploads.
                if (!wrapped && parentUid !in signedOutParents &&
                    _parentKeyChanges.value.keys.none { it.substringBefore('/') == parentUid }
                ) allWrapped = false
            }
            childEncryptionReady = allWrapped
            // Defense-in-depth: once this child has been fully ready, its own
            // enforcement reads refuse plaintext too (the child doesn't run the
            // parent-side migrator). Sticky — it only ever reads its own data.
            if (allWrapped) markEncryptionCutover(childId)
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
    fun sealCoords(childId: String, lat: Double, lng: Double, slot: String? = null): String? {
        val key = encryptionKeyIfReady() ?: return null
        val packed = java.nio.ByteBuffer.allocate(16).putDouble(lat).putDouble(lng).array()
        return b64(E2ee.sealPayload(key, packed, writerAad(childId, writerIsChild = true, slot)))
    }

    /** Child: seal raw bytes (e.g. a screenshot) under the current key, or null if not ready. */
    fun sealBytesIfReady(childId: String, bytes: ByteArray, slot: String? = null): ByteArray? =
        encryptionKeyIfReady()?.let { E2ee.sealPayload(it, bytes, writerAad(childId, writerIsChild = true, slot)) }

    /** Parent: decrypt raw bytes, warming + trying the child's key-ring; null if unavailable. */
    suspend fun openBytes(childId: String, viewerUid: String?, blob: ByteArray, slot: String? = null): ByteArray? {
        warmReaderKey(childId, viewerUid)
        return tryOpenSlot(readerRing(childId, viewerUid), blob, childId, slot).also {
            if (it == null) {
                diagnostics.info("e2ee", "bytes decrypt miss (no key) child=$childId viewer=$viewerUid")
                maybeRequestResync(childId, viewerUid)
            }
        }
    }

    /**
     * A reader that can't decrypt asks the child to re-wrap the data key for it now
     * (self-heal when a parent's device key rotated). Throttled per child so a read
     * burst sends a single request; no-op when this device IS the child.
     */
    private fun maybeRequestResync(childId: String, viewerUid: String?) {
        if (viewerUid == null || viewerUid == childId) return
        val tag = "$childId/$viewerUid"
        val now = System.currentTimeMillis()
        if (now - (lastResyncRequest[tag] ?: 0L) < RESYNC_THROTTLE_MS) return
        lastResyncRequest[tag] = now
        diagnostics.warn("e2ee", "decrypt miss; asking child to re-share and refreshing key child=$childId viewer=$viewerUid")
        scope.launch {
            runCatching { keys.requestResync(childId, viewerUid) }
            // The child may have re-wrapped a newer key-ring (a key rotation, or a
            // restored phone's new key): re-derive it, or this cached copy stays stale.
            val previous = parentRingCache.remove(childId)
            val fresh = runCatching { dataKeyForChild(childId, viewerUid, null) }.getOrNull()
            if (fresh == null && previous != null) parentRingCache.putIfAbsent(childId, previous)
        }
    }

    /** Child: reader uids currently asking for a re-wrap. */
    fun resyncRequests(childId: String): Flow<List<String>> = keys.observeResyncRequests(childId)

    /** Child: re-wrap the data key for [parentUid] immediately and clear the request. */
    suspend fun honorResync(childId: String, parentUid: String) = withContext(Dispatchers.Default) {
        verifiedParents.remove("$childId/$parentUid")
        if (ensureWrappedFor(childId, parentUid)) {
            keys.clearResync(childId, parentUid)
        }
    }

    /** Parent: decrypt a coordinate blob using the already-cached key-ring; null if unavailable. */
    fun openCoordsCached(childId: String, sealedB64: String, slot: String? = null): Pair<Double, Double>? {
        val raw = tryOpenSlot(parentRingCache[childId], unb64(sealedB64), childId, slot)
        if (raw == null) {
            // Like other reads: a miss may mean the child moved to a newer key-ring
            // (rotation, restore), so fetch it instead of showing no location forever.
            maybeRequestResync(childId, parentViewerUid)
            return null
        }
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
    suspend fun sealString(childId: String, writerUid: String?, plaintext: String, slot: String? = null): String? =
        withContext(Dispatchers.Default) {
            // Encrypt with the newest key (ring[0]); the child uses its ready-gated
            // key, a parent its unwrapped ring's newest entry.
            val key: ByteArray? = if (writerUid == childId) encryptionKeyIfReady()
            else writerUid?.let { dataKeyForChild(childId, it, null)?.firstOrNull() }
            key?.let {
                b64(E2ee.sealPayload(it, plaintext.toByteArray(Charsets.UTF_8), writerAad(childId, writerUid == childId, slot)))
            }
        }

    /** Ensure the key-ring for reading [childId] is cached, whichever side this device is. */
    suspend fun warmReaderKey(childId: String, viewerUid: String?) {
        withContext(Dispatchers.Default) {
            if (viewerUid != null && viewerUid == childId) childKeyring()
            else viewerUid?.let { dataKeyForChild(childId, it, null) }
        }
    }

    /** Decrypt a string sealed for [childId], using the already-cached key-ring; null if unavailable. */
    fun openStringCached(childId: String, viewerUid: String?, sealedB64: String, slot: String? = null): String? {
        val raw = tryOpenSlot(readerRing(childId, viewerUid), unb64(sealedB64), childId, slot)
        if (raw == null) {
            diagnostics.info("e2ee", "string decrypt miss (no key) child=$childId viewer=$viewerUid")
            maybeRequestResync(childId, viewerUid)
            return null
        }
        return runCatching { String(raw, Charsets.UTF_8) }.getOrNull()
    }

    private suspend fun ensureWrappedFor(childId: String, parentUid: String): Boolean {
        val tag = "$childId/$parentUid"
        val now = System.currentTimeMillis()
        val lastVerified = verifiedParents[tag]
        if (lastVerified != null && now - lastVerified < VERIFY_TTL_MS) return true
        // Old Watch Dog versions read the single per-account wrap; current ones read
        // their own per-device wrap. Keep both up to date.
        // Devices first: they record phones that signed out, which the shared-key
        // check then refuses.
        val devices = ensureDeviceWraps(childId, parentUid)
        val legacy = ensureLegacyWrap(childId, parentUid)
        if (legacy || devices) {
            verifiedParents[tag] = now
            signedOutParents.remove(parentUid)
        }
        return legacy || devices
    }

    /**
     * One wrap per Watch Dog device (publicKeys/{uid}/devices/{deviceId}). A device
     * is trusted when it's the parent's first key, has the same key as the one
     * already trusted, or this phone already shared with it; any other new device
     * or changed key waits for approval on this phone. Devices the parent signed
     * out of lose their wrap, and the key-ring rotates so they can't read new data.
     */
    private suspend fun ensureDeviceWraps(childId: String, parentUid: String): Boolean {
        val devices = keys.listParentDevicesFresh(parentUid) ?: return false
        if (devices.isNotEmpty()) {
            parentReadsSlots[parentUid] = (parentReadsSlots[parentUid] ?: true) && devices.all { it.aad2 }
        }
        val hasAnyPin = parentPins.all.keys.any { it == PIN_PREFIX + parentUid || it.startsWith("$PIN_PREFIX$parentUid/") }
        val legacyPin = parentPins.getString(PIN_PREFIX + parentUid, null)
        val revoked = revokedFps(parentUid)
        noDevices[parentUid] = devices.isEmpty()
        var ringBytes = encodeRing(childKeyring())
        var ringFp = E2ee.fingerprint(ringBytes)
        var any = false
        for (d in devices) {
            val key = "$parentUid/${d.deviceId}"
            val fp = E2ee.fingerprint(unb64(d.publicKeyB64))
            val pinned = parentPins.getString(PIN_PREFIX + key, null)
            val trusted = SharedKeyPolicy.deviceTrustedOnSight(fp, pinned, hasAnyPin, legacyPin, revoked) ||
                keys.getDeviceWrap(childId, parentUid, d.deviceId, fresh = true)?.parentKeyFp == fp
            if (!trusted) {
                markPending(key, fp, d.model)
                continue
            }
            if (pinned != fp) parentPins.edit().putString(PIN_PREFIX + key, fp).apply()
            if (fp in revoked) {
                setRevoked(parentUid, revokedFps(parentUid) - fp)
                diagnostics.info("e2ee", "parent phone signed back in, trusted again parent=$parentUid device=${d.deviceId}")
            }
            clearPending(key)
            val existing = keys.getDeviceWrap(childId, parentUid, d.deviceId)
            if (existing == null || existing.parentKeyFp != fp || existing.ringFp != ringFp) {
                val wrapped = deviceKeyStore.wrapDataKeyFor(unb64(d.publicKeyB64), ringBytes)
                keys.putDeviceWrap(childId, parentUid, d.deviceId, b64(wrapped), ringFp, fp)
            }
            any = true
        }
        // Signed-out / removed devices: drop their wrap and rotate the ring.
        val current = devices.map { it.deviceId }.toSet()
        val gone = keys.listDeviceWrapIds(childId, parentUid).filter { it !in current }
        _parentKeyChanges.value.keys
            .filter { it.substringBefore('/') == parentUid && it.contains('/') && it.substringAfter('/') !in current }
            .forEach { clearPending(it) }
        if (gone.isNotEmpty()) {
            val goneFps = HashSet<String>()
            gone.forEach { id ->
                val goneFp = parentPins.getString("$PIN_PREFIX$parentUid/$id", null)
                    ?: keys.getDeviceWrap(childId, parentUid, id, fresh = true)?.parentKeyFp
                goneFp?.let { goneFps += it }
                keys.deleteDeviceWrap(childId, parentUid, id)
                parentPins.edit().remove("$PIN_PREFIX$parentUid/$id").apply()
            }
            // The shared key may still be one of these phones' keys; it is refused too.
            // A key still listed for another of the parent's phones isn't revoked.
            val stillListed = devices.mapTo(HashSet()) { E2ee.fingerprint(unb64(it.publicKeyB64)) }
            setRevoked(parentUid, revoked + (goneFps - stillListed))
            rotateKeyring()
            verifiedParents.clear()
            diagnostics.info("e2ee", "parent devices removed parent=$parentUid count=${gone.size}; key-ring rotated")
        }
        return any
    }

    private suspend fun ensureLegacyWrap(childId: String, parentUid: String): Boolean {
        // Key checks use the SERVER's copy: a stale offline-cache read once pinned an
        // out-of-date key and raised a false "parent's phone changed". Offline, fall
        // back to the cached key for wrapping (so encryption never stalls) but make
        // no pin decisions until the server can be checked.
        val read = keys.readParentKeyFresh(parentUid)
        val parentPubB64 = if (read.isFailure) keys.getParentPublicKey(parentUid) else read.getOrNull()
        val parentFp = parentPubB64?.let { E2ee.fingerprint(unb64(it)) }
        val shared = when {
            read.isFailure -> SharedKeyPolicy.SharedKey.Offline(parentFp)
            parentFp == null -> SharedKeyPolicy.SharedKey.Missing
            else -> SharedKeyPolicy.SharedKey.Present(parentFp)
        }
        val pinned = parentPins.getString(PIN_PREFIX + parentUid, null)
        val revoked = revokedFps(parentUid)
        // The extra server reads are only needed when the key differs from the pin,
        // or belongs to a phone that signed out (is it back?).
        val changed = shared is SharedKeyPolicy.SharedKey.Present &&
            (parentFp in revoked || (pinned != null && pinned != parentFp))
        val action = SharedKeyPolicy.childAction(
            shared = shared,
            pinnedFp = pinned,
            revokedFps = revoked,
            sharedWrapFp = if (changed) keys.getWrappedKeyFresh(childId, parentUid)?.parentKeyFp else null,
            approvedDeviceFps = if (changed) approvedDeviceFps(parentUid) else emptySet(),
            listedDeviceFps = if (changed) {
                keys.listParentDevicesFresh(parentUid)?.mapTo(HashSet()) { E2ee.fingerprint(unb64(it.publicKeyB64)) }
            } else null,
        )
        when (action) {
            SharedKeyPolicy.ChildAction.SKIP -> return false
            SharedKeyPolicy.ChildAction.DROP -> {
                dropSharedWrap(childId, parentUid)
                if (noDevices[parentUid] == true) signedOutParents += parentUid
                return false
            }
            SharedKeyPolicy.ChildAction.ASK -> {
                markPending(parentUid, parentFp!!)
                return false
            }
            SharedKeyPolicy.ChildAction.PIN_AND_WRAP -> {
                parentPins.edit().putString(PIN_PREFIX + parentUid, parentFp!!).apply()
                clearPending(parentUid)
                if (pinned != null) diagnostics.info("e2ee", "shared parent key re-pinned (already trusted) parent=$parentUid")
            }
            SharedKeyPolicy.ChildAction.WRAP -> if (shared is SharedKeyPolicy.SharedKey.Present) {
                // The published key went back to the trusted one: nothing to approve.
                clearPending(parentUid)
            }
        }
        val pub = parentPubB64!!
        val fp = parentFp!!
        parentReadsSlots[parentUid] = keys.supportsSlotAad(parentUid)
        val ringBytes = encodeRing(childKeyring())
        val ringFp = E2ee.fingerprint(ringBytes)
        // Re-wrap when the existing wrap is missing, was sealed for a different
        // parent device key (parent reinstalled), or predates a key rotation.
        val existing = keys.getWrappedKey(childId, parentUid)
        if (existing == null || existing.parentKeyFp != fp || existing.ringFp != ringFp) {
            val wrapped = deviceKeyStore.wrapDataKeyFor(unb64(pub), ringBytes)
            keys.putWrappedKey(childId, parentUid, b64(wrapped), ringFp, fp)
        }
        return true
    }

    /**
     * The parent's shared key is gone, or belongs to a phone that signed out: delete
     * the copy of the data key sealed for it and start a new data key, so that
     * phone can't read anything written from now on.
     */
    private suspend fun dropSharedWrap(childId: String, parentUid: String) {
        clearPending(parentUid)
        if (keys.getWrappedKeyFresh(childId, parentUid) == null) return
        keys.deleteWrappedKey(childId, parentUid)
        rotateKeyring()
        verifiedParents.clear()
        diagnostics.info("e2ee", "shared parent key retired parent=$parentUid; key-ring rotated")
    }

    // ---- parent side ----

    /** Publish this parent's public key so children can wrap their data key for it. */
    suspend fun publishParentKey(parentUid: String) = withContext(Dispatchers.Default) {
        val pub = myPublicKeyB64()
        // The per-account key is still read by older Shield versions.
        val shared = keys.publishParentPublicKey(parentUid, pub)
        val device = keys.publishParentDevice(parentUid, deviceId(), pub, android.os.Build.MODEL ?: "")
        val fp = E2ee.fingerprint(unb64(pub))
        if (shared is AppResult.Success && device is AppResult.Success) {
            diagnostics.info("e2ee", "parent key published parent=$parentUid device=${deviceId()} fp=$fp")
        } else {
            diagnostics.error("e2ee", "parent key publish failed parent=$parentUid shared=${shared is AppResult.Success} device=${device is AppResult.Success}",
                (shared as? AppResult.Failure)?.error ?: (device as? AppResult.Failure)?.error)
        }
        Unit
    }

    /**
     * Parent signing out: remove this device's key so children stop sharing with it.
     * If the shared key (still read by older app versions) is this phone's, hand it
     * to the parent's most recent other phone, or delete it when none is left.
     * Offline, the children's phones catch it instead: they refuse a shared key
     * that belongs to a phone that has signed out.
     */
    suspend fun unpublishDevice(parentUid: String) {
        keys.deleteParentDevice(parentUid, deviceId())
        val mine = myPublicKeyB64()
        val shared = keys.readParentKeyFresh(parentUid).getOrNull()
        val others = keys.listParentDevicesFresh(parentUid)?.map { it.publicKeyB64 to it.updatedAt }
        val h = SharedKeyPolicy.handover(mine, shared, others)
        when (h) {
            SharedKeyPolicy.Handover.Leave -> Unit
            is SharedKeyPolicy.Handover.Replace -> keys.publishParentPublicKey(parentUid, h.publicKeyB64)
            SharedKeyPolicy.Handover.Delete -> keys.deleteParentPublicKey(parentUid)
        }
        diagnostics.info("e2ee", "signed out: device key removed parent=$parentUid sharedKey=${h::class.simpleName}")
    }

    /** This install's stable device id (one Watch Dog install = one device key). */
    fun deviceId(): String {
        val prefs = appContext.getSharedPreferences(DEVICE_PREFS, Context.MODE_PRIVATE)
        prefs.getString(DEVICE_ID, null)?.let { return it }
        val id = java.util.UUID.randomUUID().toString().replace("-", "").take(20)
        prefs.edit().putString(DEVICE_ID, id).apply()
        return id
    }

    /**
     * Trust-on-first-use key-change detection: records the first backend-published
     * child key, and on a later change logs a warning and drops any now-stale QR
     * pin so the parent is re-prompted to verify. Decryption still proceeds (a
     * legitimate reinstall rotates the key), but the verified badge is withdrawn.
     */
    private suspend fun detectChildKeyChange(childId: String, backendPubB64: String) {
        // A restore this phone did: the reinstalled install's key (from its QR) is
        // expected; pin it once the child has published it.
        if (restorePins().getString(childId, null) == backendPubB64) {
            pinnedKeys.pin(childId, backendPubB64)
            pinnedKeys.putSeenKey(childId, backendPubB64)
            restorePins().edit().remove(childId).apply()
            return
        }
        val seen = pinnedKeys.getSeenKey(childId)
        if (seen == null) {
            pinnedKeys.putSeenKey(childId, backendPubB64)
            return
        }
        if (seen != backendPubB64) {
            diagnostics.warn("e2ee", "child public key changed child=$childId")
            pinnedKeys.putSeenKey(childId, backendPubB64)
            val pinned = pinnedKeys.getPinnedKey(childId)
            if (pinned != null && pinned != backendPubB64) pinnedKeys.unpin(childId)
        }
    }

    /**
     * Parent, restoring a reinstalled Shield as [childId]: this phone's copy of the
     * child's key-ring, read fresh from the server. Null if it can't be read.
     */
    suspend fun ringForRestore(childId: String, parentUid: String): List<ByteArray>? {
        parentRingCache.remove(childId)
        val ring = dataKeyForChild(childId, parentUid, null)
        if (ring == null) diagnostics.warn("restore", "no key-ring for child=$childId on this phone device=${deviceId()}")
        else diagnostics.info("restore", "key-ring read child=$childId keys=${ring.size}")
        return ring
    }

    /**
     * Parent: hand [ring] (the child's old key-ring) to the reinstalled install,
     * wrapped for its new key [newChildPubB64] from the QR, so it can keep reading
     * and extending the old data. The new key is pinned once the child publishes it.
     */
    suspend fun handRingToRestoredChild(
        childId: String, parentUid: String, newChildPubB64: String, ring: List<ByteArray>,
    ): Boolean = withContext(Dispatchers.Default) {
        val wrapped = deviceKeyStore.wrapDataKeyFor(unb64(newChildPubB64), encodeRing(ring))
        val ok = keys.putRingImport(childId, parentUid, b64(wrapped), myPublicKeyB64()) is AppResult.Success
        if (ok) restorePins().edit().putString(childId, newChildPubB64).apply()
        diagnostics.info("restore", "key-ring handed over child=$childId ok=$ok")
        ok
    }

    /** Parent: the restore didn't happen; drop the handed-over ring. */
    suspend fun cancelRingHandoff(childId: String, parentUid: String) {
        keys.deleteRingImport(childId, parentUid)
        restorePins().edit().remove(childId).apply()
    }

    private fun restorePins() = appContext.getSharedPreferences(RESTORE_PINS_PREFS, Context.MODE_PRIVATE)

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
        parentViewerUid = parentUid
        parentRingCache[childId]?.let { return it }
        return withContext(Dispatchers.Default) {
            // This device's own wrap first; the per-account wrap for older Shields.
            val candidates = listOfNotNull(
                keys.getDeviceWrap(childId, parentUid, deviceId()),
                keys.getWrappedKey(childId, parentUid),
            )
            if (candidates.isEmpty()) return@withContext null
            // Trust-on-first-use: record the first backend key seen and alert if it
            // later changes under us (possible key substitution / unverified rotation).
            val backendPub = keys.getChildPublicKey(childId)
            if (backendPub != null) detectChildKeyChange(childId, backendPub)
            // Prefer the out-of-band pinned key (verified via QR); fall back to the
            // caller-supplied key, then the backend-published key (trust-on-first-use).
            val childPub = pinnedKeys.getPinnedKey(childId)
                ?: childPublicKeyB64
                ?: backendPub
                ?: return@withContext null
            candidates.firstNotNullOfOrNull { wrapped ->
                runCatching {
                    decodeRing(deviceKeyStore.unwrapDataKeyFrom(unb64(childPub), unb64(wrapped.wrappedB64)))
                }.getOrNull()
            }?.also { parentRingCache[childId] = it }
        }
    }

    /**
     * Parent: manually reconcile the secure link for [childId] — drop the cached
     * key-ring and ask the child to re-wrap its data key for this device now. Used
     * by the "Reconcile" button when encrypted data (location/history) won't open,
     * e.g. after a reinstall rotated this device's key. The child must be online to
     * honour it; the next read picks up the fresh wrap.
     */
    suspend fun reconcileAsParent(childId: String, parentUid: String) {
        parentRingCache.remove(childId)
        lastResyncRequest.remove("$childId/$parentUid")
        runCatching { keys.requestResync(childId, parentUid) }
        runCatching { warmDataKey(childId, parentUid) }
    }

    /**
     * Child: manually re-publish this device's public key and re-wrap the data key
     * for all [linkedParents] immediately, bypassing the periodic TTL. Used by the
     * child's "Re-sync security" button to unstick a parent that can't decrypt.
     */
    suspend fun reconcileAsChild(childId: String, linkedParents: List<String>) {
        verifiedParents.clear()
        childKeyPublished = false
        syncChildKeys(childId, linkedParents)
    }

    /**
     * Drop in-memory session key material on sign-out, so a different account
     * signing in on the same device starts clean. The device identity keypair
     * (per-device, not per-account) is intentionally kept.
     */
    fun clearSession() {
        parentViewerUid = null
        parentRingCache.clear()
        verifiedParents.clear()
        signedOutParents.clear()
        noDevices.clear()
        lastLinkedParents = null
        childKeyPublished = false
        childEncryptionReady = false
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        const val PARENT_PINS_PREFS = "e2ee_parent_pins"
        const val PENDING_PREFIX = "pending:"
        private const val MODEL_PREFIX = "model:"
        private const val DEVICE_PREFS = "e2ee_device"
        private const val DEVICE_ID = "id"
        private const val PIN_PREFIX = "pin:"
        private const val EMAIL_PREFIX = "email:"
        /** String set of the key fingerprints of a parent's phones that signed out. */
        private const val REVOKED_PREFIX = "revoked:"
        private const val RESTORE_PINS_PREFS = "e2ee_restore_pins"
        /** Ring size cap (the ring's encoding counts keys in one byte). */
        private const val MAX_RING = 64
        /**
         * P6 encrypt-only cutover. Keep false until every active device has
         * completed key exchange (verified on real hardware); flipping it true
         * makes child writers stop emitting plaintext. One-way for new data — do
         * not flip without that verification.
         */
        const val ENCRYPT_ONLY = false

        /** How often the child re-confirms each parent's wrapped key is current. */
        // Short so a parent that reinstalled / rotated its device key (common on a
        // shared test device) gets its wrap refreshed within minutes, not half an
        // hour — the window during which the parent can't decrypt the child's data.
        private const val VERIFY_TTL_MS = 3L * 60 * 1000
        private const val RESYNC_THROTTLE_MS = 60L * 1000 // at most one re-sync request per child per minute
    }
}
