package com.shahaabapps.core.firebase.e2ee

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Stores and exchanges E2EE public keys and wrapped data keys. Only public keys
 * and per-parent *wrapped* (encrypted) data keys ever touch the backend — never
 * a private key or a plaintext data key.
 *
 * Layout:
 *  - `publicKeys/{uid}`                          — a device public key (b64), for
 *    both children and parents; a key directory readable by any signed-in user
 *    (public keys aren't secret) and writable only by its owner.
 *  - `children/{childId}/e2eeKeys/{parentUid}`   — { wrapped, ringFp, parentKeyFp, updatedAt }
 *  - `publicKeys/{uid}/devices/{deviceId}`       — one key per Watch Dog install, so a
 *    parent signed in on several devices doesn't overwrite one shared key.
 *  - `children/{childId}/e2eeKeys/{parentUid}/devices/{deviceId}` — the data key
 *    wrapped for that one device.
 */
interface E2eeKeyRepository {
    suspend fun publishChildPublicKey(childId: String, publicKeyB64: String): AppResult<Unit>
    suspend fun publishParentPublicKey(parentUid: String, publicKeyB64: String): AppResult<Unit>
    /** Parent: remove the shared key (the last of their phones signed out). */
    suspend fun deleteParentPublicKey(parentUid: String): AppResult<Unit>

    suspend fun getChildPublicKey(childId: String): String?
    suspend fun getParentPublicKey(parentUid: String): String?

    /** Whether [uid]'s app reads slot-bound (per-document) payloads. */
    suspend fun supportsSlotAad(uid: String): Boolean
    fun observeParentPublicKey(parentUid: String): Flow<String?>

    suspend fun putWrappedKey(
        childId: String,
        parentUid: String,
        wrappedB64: String,
        ringFp: String,
        parentKeyFp: String,
    ): AppResult<Unit>

    suspend fun getWrappedKey(childId: String, parentUid: String): WrappedKey?

    /**
     * The parent's published key and the existing wrap, read from the SERVER (never
     * the offline cache), for key-change checks. The key read fails when the server
     * can't be reached, and succeeds with null when the parent has no key; the wrap
     * read is null in either case.
     */
    suspend fun readParentKeyFresh(parentUid: String): Result<String?>
    suspend fun getWrappedKeyFresh(childId: String, parentUid: String): WrappedKey?
    fun observeWrappedKey(childId: String, parentUid: String): Flow<WrappedKey?>
    suspend fun deleteWrappedKey(childId: String, parentUid: String): AppResult<Unit>

    /**
     * A reader (parent/viewer) that can't decrypt asks the child to re-wrap the
     * data key for it now, instead of waiting for the child's next periodic sync.
     * `children/{childId}/keyResync/{parentUid}`.
     */
    suspend fun requestResync(childId: String, parentUid: String): AppResult<Unit>
    /** Child: observe the set of reader uids currently asking for a re-wrap. */
    fun observeResyncRequests(childId: String): Flow<List<String>>
    /** Child: clear a request once it has been honoured. */
    suspend fun clearResync(childId: String, parentUid: String): AppResult<Unit>

    // ---- per-device keys ----
    suspend fun publishParentDevice(parentUid: String, deviceId: String, publicKeyB64: String, model: String): AppResult<Unit>
    suspend fun deleteParentDevice(parentUid: String, deviceId: String): AppResult<Unit>
    /** The parent's devices, from the server; null when the server can't be reached. */
    suspend fun listParentDevicesFresh(parentUid: String): List<ParentDevice>?
    fun observeParentDevices(parentUid: String): Flow<List<ParentDevice>>
    suspend fun putDeviceWrap(childId: String, parentUid: String, deviceId: String, wrappedB64: String, ringFp: String, parentKeyFp: String): AppResult<Unit>
    suspend fun getDeviceWrap(childId: String, parentUid: String, deviceId: String, fresh: Boolean = false): WrappedKey?
    suspend fun deleteDeviceWrap(childId: String, parentUid: String, deviceId: String): AppResult<Unit>
    /** Device ids that currently have a wrap under this parent (for clean-up). */
    suspend fun listDeviceWrapIds(childId: String, parentUid: String): List<String>

    // ---- restore: the old key-ring handed to a reinstalled install ----
    suspend fun putRingImport(childId: String, parentUid: String, wrappedB64: String, senderPubB64: String): AppResult<Unit>
    suspend fun listRingImports(childId: String): List<RingImport>
    suspend fun deleteRingImport(childId: String, parentUid: String): AppResult<Unit>
}

/** A key-ring a parent handed to this install after a restore. */
data class RingImport(val parentUid: String, val wrappedB64: String, val senderPubB64: String)

/** One Watch Dog install's published key. */
data class ParentDevice(
    val deviceId: String,
    val publicKeyB64: String,
    val aad2: Boolean,
    val model: String,
    val updatedAt: Long,
)

/**
 * A child's key-ring encrypted (wrapped) for one parent. [ringFp] fingerprints
 * the ring contents, so the child re-wraps after a key rotation; [parentKeyFp]
 * fingerprints the parent device key it was sealed for, so the child re-wraps
 * when a parent reinstalls with a new key.
 */
data class WrappedKey(
    val wrappedB64: String,
    val ringFp: String,
    val parentKeyFp: String = "",
)

class FirebaseE2eeKeyRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : E2eeKeyRepository {

    override suspend fun publishChildPublicKey(childId: String, publicKeyB64: String): AppResult<Unit> =
        publishPublicKey(childId, publicKeyB64)

    override suspend fun publishParentPublicKey(parentUid: String, publicKeyB64: String): AppResult<Unit> =
        publishPublicKey(parentUid, publicKeyB64)

    override suspend fun deleteParentPublicKey(parentUid: String): AppResult<Unit> =
        runCatchingResult { firestore.collection(PUBLIC_KEYS).document(parentUid).delete().await() }

    private suspend fun publishPublicKey(uid: String, publicKeyB64: String): AppResult<Unit> =
        runCatchingResult {
            firestore.collection(PUBLIC_KEYS).document(uid)
                // aad2: this app reads payloads bound to their document (slot).
                .set(mapOf(FIELD_PUBLIC_KEY to publicKeyB64, FIELD_AAD2 to true), SetOptions.merge()).await()
        }

    override suspend fun getChildPublicKey(childId: String): String? = getPublicKey(childId)

    override suspend fun getParentPublicKey(parentUid: String): String? = getPublicKey(parentUid)

    override suspend fun supportsSlotAad(uid: String): Boolean = runCatching {
        firestore.collection(PUBLIC_KEYS).document(uid).get().await().getBoolean(FIELD_AAD2) == true
    }.getOrDefault(false)

    private suspend fun getPublicKey(uid: String): String? = runCatching {
        firestore.collection(PUBLIC_KEYS).document(uid).get().await().getString(FIELD_PUBLIC_KEY)
    }.getOrNull()?.ifBlank { null }

    override fun observeParentPublicKey(parentUid: String): Flow<String?> = callbackFlow {
        val reg = firestore.collection(PUBLIC_KEYS).document(parentUid)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(null); return@addSnapshotListener }
                trySend(snap?.getString(FIELD_PUBLIC_KEY)?.ifBlank { null })
            }
        awaitClose { reg.remove() }
    }

    override suspend fun putWrappedKey(
        childId: String,
        parentUid: String,
        wrappedB64: String,
        ringFp: String,
        parentKeyFp: String,
    ): AppResult<Unit> = runCatchingResult {
        wrappedDoc(childId, parentUid).set(
            mapOf(
                "wrapped" to wrappedB64,
                "ringFp" to ringFp,
                "parentKeyFp" to parentKeyFp,
                "updatedAt" to System.currentTimeMillis(),
            ),
        ).await()
    }

    override suspend fun readParentKeyFresh(parentUid: String): Result<String?> = runCatching {
        firestore.collection(PUBLIC_KEYS).document(parentUid)
            .get(com.google.firebase.firestore.Source.SERVER).await().getString(FIELD_PUBLIC_KEY)?.ifBlank { null }
    }

    override suspend fun getWrappedKeyFresh(childId: String, parentUid: String): WrappedKey? = runCatching {
        val doc = wrappedDoc(childId, parentUid).get(com.google.firebase.firestore.Source.SERVER).await()
        val wrapped = doc.getString("wrapped") ?: return@runCatching null
        WrappedKey(wrapped, doc.getString("ringFp") ?: "", doc.getString("parentKeyFp") ?: "")
    }.getOrNull()

    override suspend fun getWrappedKey(childId: String, parentUid: String): WrappedKey? = runCatching {
        val doc = wrappedDoc(childId, parentUid).get().await()
        val wrapped = doc.getString("wrapped") ?: return@runCatching null
        WrappedKey(wrapped, doc.getString("ringFp") ?: "", doc.getString("parentKeyFp") ?: "")
    }.getOrNull()

    override fun observeWrappedKey(childId: String, parentUid: String): Flow<WrappedKey?> = callbackFlow {
        val reg = wrappedDoc(childId, parentUid).addSnapshotListener { snap, err ->
            if (err != null || snap == null) { trySend(null); return@addSnapshotListener }
            val wrapped = snap.getString("wrapped")
            trySend(
                if (wrapped == null) null
                else WrappedKey(wrapped, snap.getString("ringFp") ?: "", snap.getString("parentKeyFp") ?: ""),
            )
        }
        awaitClose { reg.remove() }
    }

    override suspend fun deleteWrappedKey(childId: String, parentUid: String): AppResult<Unit> =
        runCatchingResult { wrappedDoc(childId, parentUid).delete().await() }

    override suspend fun requestResync(childId: String, parentUid: String): AppResult<Unit> =
        runCatchingResult {
            resyncDoc(childId, parentUid).set(mapOf("at" to System.currentTimeMillis())).await()
        }

    override fun observeResyncRequests(childId: String): Flow<List<String>> = callbackFlow {
        val reg = firestore.collection(CHILDREN).document(childId).collection(KEY_RESYNC)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(snap.documents.map { it.id })
            }
        awaitClose { reg.remove() }
    }

    override suspend fun clearResync(childId: String, parentUid: String): AppResult<Unit> =
        runCatchingResult { resyncDoc(childId, parentUid).delete().await() }

    private fun wrappedDoc(childId: String, parentUid: String) =
        firestore.collection(CHILDREN).document(childId)
            .collection(E2EE_KEYS).document(parentUid)

    private fun resyncDoc(childId: String, parentUid: String) =
        firestore.collection(CHILDREN).document(childId)
            .collection(KEY_RESYNC).document(parentUid)

    private companion object {
        const val CHILDREN = "children"
        const val PUBLIC_KEYS = "publicKeys"
        const val E2EE_KEYS = "e2eeKeys"
        const val KEY_RESYNC = "keyResync"
        const val FIELD_PUBLIC_KEY = "e2ePublicKey"
        const val FIELD_AAD2 = "aad2"
        const val DEVICES = "devices"
        const val RING_IMPORT = "ringImport"
    }

    // ---- per-device keys ----

    private fun deviceDoc(parentUid: String, deviceId: String) =
        firestore.collection(PUBLIC_KEYS).document(parentUid).collection(DEVICES).document(deviceId)

    private fun deviceWrapDoc(childId: String, parentUid: String, deviceId: String) =
        wrappedDoc(childId, parentUid).collection(DEVICES).document(deviceId)

    override suspend fun publishParentDevice(parentUid: String, deviceId: String, publicKeyB64: String, model: String): AppResult<Unit> =
        runCatchingResult {
            deviceDoc(parentUid, deviceId).set(
                mapOf(
                    FIELD_PUBLIC_KEY to publicKeyB64,
                    FIELD_AAD2 to true,
                    "model" to model.take(60),
                    "updatedAt" to System.currentTimeMillis(),
                ),
            ).await()
        }

    override suspend fun deleteParentDevice(parentUid: String, deviceId: String): AppResult<Unit> =
        runCatchingResult { deviceDoc(parentUid, deviceId).delete().await() }

    private fun com.google.firebase.firestore.DocumentSnapshot.toParentDevice(): ParentDevice? {
        val key = getString(FIELD_PUBLIC_KEY)?.ifBlank { null } ?: return null
        return ParentDevice(id, key, getBoolean(FIELD_AAD2) == true, getString("model").orEmpty(), getLong("updatedAt") ?: 0L)
    }

    override suspend fun listParentDevicesFresh(parentUid: String): List<ParentDevice>? = runCatching {
        firestore.collection(PUBLIC_KEYS).document(parentUid).collection(DEVICES)
            .get(com.google.firebase.firestore.Source.SERVER).await()
            .documents.mapNotNull { it.toParentDevice() }
    }.getOrNull()

    override fun observeParentDevices(parentUid: String): Flow<List<ParentDevice>> = callbackFlow {
        val reg = firestore.collection(PUBLIC_KEYS).document(parentUid).collection(DEVICES)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                trySend(snap.documents.mapNotNull { it.toParentDevice() })
            }
        awaitClose { reg.remove() }
    }

    override suspend fun putDeviceWrap(
        childId: String, parentUid: String, deviceId: String, wrappedB64: String, ringFp: String, parentKeyFp: String,
    ): AppResult<Unit> = runCatchingResult {
        deviceWrapDoc(childId, parentUid, deviceId).set(
            mapOf("wrapped" to wrappedB64, "ringFp" to ringFp, "parentKeyFp" to parentKeyFp, "updatedAt" to System.currentTimeMillis()),
        ).await()
    }

    override suspend fun getDeviceWrap(childId: String, parentUid: String, deviceId: String, fresh: Boolean): WrappedKey? = runCatching {
        val ref = deviceWrapDoc(childId, parentUid, deviceId)
        val doc = if (fresh) ref.get(com.google.firebase.firestore.Source.SERVER).await() else ref.get().await()
        val wrapped = doc.getString("wrapped") ?: return@runCatching null
        WrappedKey(wrapped, doc.getString("ringFp") ?: "", doc.getString("parentKeyFp") ?: "")
    }.getOrNull()

    override suspend fun deleteDeviceWrap(childId: String, parentUid: String, deviceId: String): AppResult<Unit> =
        runCatchingResult { deviceWrapDoc(childId, parentUid, deviceId).delete().await() }

    override suspend fun listDeviceWrapIds(childId: String, parentUid: String): List<String> = runCatching {
        wrappedDoc(childId, parentUid).collection(DEVICES).get().await().documents.map { it.id }
    }.getOrDefault(emptyList())

    // ---- restore ----

    private fun ringImportDoc(childId: String, parentUid: String) =
        firestore.collection("children").document(childId).collection(RING_IMPORT).document(parentUid)

    override suspend fun putRingImport(
        childId: String, parentUid: String, wrappedB64: String, senderPubB64: String,
    ): AppResult<Unit> = runCatchingResult {
        ringImportDoc(childId, parentUid).set(
            mapOf("wrapped" to wrappedB64, "senderPub" to senderPubB64, "at" to System.currentTimeMillis()),
        ).await()
    }

    override suspend fun listRingImports(childId: String): List<RingImport> = runCatching {
        firestore.collection("children").document(childId).collection(RING_IMPORT)
            .get(com.google.firebase.firestore.Source.SERVER).await().documents.mapNotNull { d ->
                RingImport(d.id, d.getString("wrapped") ?: return@mapNotNull null, d.getString("senderPub") ?: return@mapNotNull null)
            }
    }.getOrDefault(emptyList())

    override suspend fun deleteRingImport(childId: String, parentUid: String): AppResult<Unit> =
        runCatchingResult { ringImportDoc(childId, parentUid).delete().await() }
}
