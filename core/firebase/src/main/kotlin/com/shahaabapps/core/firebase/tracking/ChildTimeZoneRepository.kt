package com.shahaabapps.core.firebase.tracking

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * The child's home time zone, which schedules (bedtime, school, allowed hours,
 * daily-limit days) follow — set by the parent, so changing the phone's own time
 * zone doesn't move bedtime.
 *
 * Firestore layout: `children/{childId}/settings/timezone` { zoneId, updatedAtMillis }.
 * Parent-write only (settings rules); Shield reads it. Null = not set yet (Shield
 * then uses the phone's zone from when it was first set up).
 */
class ChildTimeZoneRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    fun observe(childId: String): Flow<String?> = callbackFlow {
        val registration = doc(childId).addSnapshotListener { snapshot, error ->
            // On an error keep whatever Shield last stored (no emission), rather
            // than falling back to the phone's zone.
            if (error != null) return@addSnapshotListener
            trySend(snapshot?.getString("zoneId")?.ifBlank { null })
        }
        awaitClose { registration.remove() }
    }

    suspend fun setZone(childId: String, zoneId: String): AppResult<Unit> =
        runCatchingResult {
            doc(childId).set(mapOf("zoneId" to zoneId, "updatedAtMillis" to System.currentTimeMillis())).await()
            Unit
        }

    private fun doc(childId: String) =
        firestore.collection("children").document(childId)
            .collection("settings").document("timezone")
}
