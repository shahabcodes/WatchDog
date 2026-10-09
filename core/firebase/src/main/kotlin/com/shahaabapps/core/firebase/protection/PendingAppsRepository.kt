package com.shahaabapps.core.firebase.protection

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.model.PendingApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Apps auto-blocked on install, awaiting parent review.
 *
 * Firestore: `children/{childId}/pendingApps/{packageName}`. The child creates
 * them; the parent reads and resolves (deletes) them.
 */
interface PendingAppsRepository {
    fun observe(childId: String): Flow<List<PendingApp>>
    suspend fun add(childId: String, app: PendingApp): AppResult<Unit>
    suspend fun remove(childId: String, packageName: String): AppResult<Unit>
}

class FirebasePendingAppsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : PendingAppsRepository {

    override fun observe(childId: String): Flow<List<PendingApp>> = callbackFlow {
        val registration = collection(childId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(
                snapshot?.documents?.map {
                    PendingApp(
                        packageName = it.getString("packageName") ?: it.id,
                        label = it.getString("label") ?: "",
                        installedAtMillis = it.getLong("installedAtMillis") ?: 0L,
                    )
                } ?: emptyList(),
            )
        }
        awaitClose { registration.remove() }
    }

    override suspend fun add(childId: String, app: PendingApp): AppResult<Unit> =
        runCatchingResult {
            collection(childId).document(app.packageName).set(
                mapOf(
                    "packageName" to app.packageName,
                    "label" to app.label,
                    "installedAtMillis" to app.installedAtMillis,
                ),
            ).await()
            Unit
        }

    override suspend fun remove(childId: String, packageName: String): AppResult<Unit> =
        runCatchingResult {
            collection(childId).document(packageName).delete().await()
            Unit
        }

    private fun collection(childId: String) =
        firestore.collection("children").document(childId).collection("pendingApps")
}
