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
 */
interface E2eeKeyRepository {
    suspend fun publishChildPublicKey(childId: String, publicKeyB64: String): AppResult<Unit>
    suspend fun publishParentPublicKey(parentUid: String, publicKeyB64: String): AppResult<Unit>

    suspend fun getChildPublicKey(childId: String): String?
    suspend fun getParentPublicKey(parentUid: String): String?
    fun observeParentPublicKey(parentUid: String): Flow<String?>

    suspend fun putWrappedKey(
        childId: String,
        parentUid: String,
        wrappedB64: String,
        ringFp: String,
        parentKeyFp: String,
    ): AppResult<Unit>

    suspend fun getWrappedKey(childId: String, parentUid: String): WrappedKey?
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
}

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

    private suspend fun publishPublicKey(uid: String, publicKeyB64: String): AppResult<Unit> =
        runCatchingResult {
            firestore.collection(PUBLIC_KEYS).document(uid)
                .set(mapOf(FIELD_PUBLIC_KEY to publicKeyB64), SetOptions.merge()).await()
        }

    override suspend fun getChildPublicKey(childId: String): String? = getPublicKey(childId)

    override suspend fun getParentPublicKey(parentUid: String): String? = getPublicKey(parentUid)

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
    }
}
