package com.shahaabapps.core.firebase.tracking

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.model.TrackingMode
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Location-tracking accuracy mode chosen by the parent.
 *
 * Firestore layout: `children/{childId}/settings/tracking` { mode }. The parent
 * writes it from Watch Dog; the child (Shield) reads it to pick its sampling.
 */
interface TrackingRepository {
    fun observe(childId: String): Flow<TrackingMode>
    suspend fun setMode(childId: String, mode: TrackingMode): AppResult<Unit>
}

class FirebaseTrackingRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : TrackingRepository {

    override fun observe(childId: String): Flow<TrackingMode> = callbackFlow {
        val registration = doc(childId).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(TrackingMode.DEFAULT)
                return@addSnapshotListener
            }
            trySend(TrackingMode.from(snapshot.getString("mode")))
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setMode(childId: String, mode: TrackingMode): AppResult<Unit> =
        runCatchingResult {
            doc(childId).set(
                mapOf("mode" to mode.name, "updatedAtMillis" to System.currentTimeMillis()),
            ).await()
            Unit
        }

    private fun doc(childId: String) =
        firestore.collection("children").document(childId)
            .collection("settings").document("tracking")
}
