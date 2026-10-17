package com.shahaabapps.core.firebase.viewer

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Parent switch that allows read-only viewers to join a child with the pairing
 * code. Firestore layout: `children/{childId}/settings/viewerAccess` { enabled }.
 * Parent writes it; the child + the viewer-join Cloud Function read it.
 */
interface ViewerAccessRepository {
    fun observe(childId: String): Flow<Boolean>
    suspend fun setEnabled(childId: String, enabled: Boolean): AppResult<Unit>
}

class FirebaseViewerAccessRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ViewerAccessRepository {

    override fun observe(childId: String): Flow<Boolean> = callbackFlow {
        val registration = doc(childId).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(false)
                return@addSnapshotListener
            }
            trySend(snapshot.getBoolean("enabled") ?: false)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setEnabled(childId: String, enabled: Boolean): AppResult<Unit> =
        runCatchingResult {
            doc(childId).set(
                mapOf("enabled" to enabled, "updatedAtMillis" to System.currentTimeMillis()),
            ).await()
            Unit
        }

    private fun doc(childId: String) =
        firestore.collection("children").document(childId)
            .collection("settings").document("viewerAccess")
}
