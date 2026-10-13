package com.shahaabapps.core.firebase.request

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.model.RequestKind
import com.shahaabapps.core.model.RequestStatus
import com.shahaabapps.core.model.TimeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Child "asks" (more time, unlock an app, …). The child creates them; a linked
 * parent reads and responds. Firestore: `children/{childId}/requests/{id}`.
 */
interface RequestRepository {
    /** Child creates a new ask. */
    suspend fun create(childId: String, request: TimeRequest): AppResult<Unit>

    /** Newest-first stream of the child's requests (both apps observe this). */
    fun observe(childId: String, limit: Long = 30): Flow<List<TimeRequest>>

    /** Parent approves or denies a pending ask. */
    suspend fun respond(childId: String, requestId: String, approved: Boolean): AppResult<Unit>

    /** Child withdraws a pending ask. */
    suspend fun cancel(childId: String, requestId: String): AppResult<Unit>
}

class FirebaseRequestRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : RequestRepository {

    override suspend fun create(childId: String, request: TimeRequest): AppResult<Unit> =
        runCatchingResult {
            val data = mapOf(
                "kind" to request.kind.name,
                "appPackage" to request.appPackage,
                "appLabel" to request.appLabel,
                "minutes" to request.minutes,
                "status" to RequestStatus.PENDING.name,
                "createdAtMillis" to System.currentTimeMillis(),
                "respondedAtMillis" to 0L,
            )
            requests(childId).add(data).await()
            Unit
        }

    override fun observe(childId: String, limit: Long): Flow<List<TimeRequest>> = callbackFlow {
        val registration = requests(childId)
            .orderBy("createdAtMillis", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList()); return@addSnapshotListener
                }
                trySend(snapshot?.documents?.map { it.toRequest() } ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun respond(childId: String, requestId: String, approved: Boolean): AppResult<Unit> =
        runCatchingResult {
            requests(childId).document(requestId).update(
                mapOf(
                    "status" to (if (approved) RequestStatus.APPROVED else RequestStatus.DENIED).name,
                    "respondedAtMillis" to System.currentTimeMillis(),
                ),
            ).await()
            Unit
        }

    override suspend fun cancel(childId: String, requestId: String): AppResult<Unit> =
        runCatchingResult { requests(childId).document(requestId).delete().await(); Unit }

    private fun requests(childId: String) =
        firestore.collection("children").document(childId).collection("requests")

    private fun DocumentSnapshot.toRequest() = TimeRequest(
        id = id,
        kind = getString("kind")?.let { runCatching { RequestKind.valueOf(it) }.getOrNull() } ?: RequestKind.MORE_TIME,
        appPackage = getString("appPackage") ?: "",
        appLabel = getString("appLabel") ?: "",
        minutes = (getLong("minutes") ?: 0L).toInt(),
        status = getString("status")?.let { runCatching { RequestStatus.valueOf(it) }.getOrNull() } ?: RequestStatus.PENDING,
        createdAtMillis = getLong("createdAtMillis") ?: 0L,
        respondedAtMillis = getLong("respondedAtMillis") ?: 0L,
    )
}
