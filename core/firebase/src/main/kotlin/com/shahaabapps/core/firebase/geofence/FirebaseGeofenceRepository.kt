package com.shahaabapps.core.firebase.geofence

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.model.Geofence
import com.shahaabapps.core.model.GeofenceEvent
import com.shahaabapps.core.model.GeofenceTransition
import com.shahaabapps.core.model.GeofenceTrigger
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseGeofenceRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : GeofenceRepository {

    override fun observeGeofences(childId: String): Flow<List<Geofence>> = callbackFlow {
        val registration = geofences(childId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList()); return@addSnapshotListener
            }
            trySend(snapshot?.documents?.map { it.toGeofence() } ?: emptyList())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun saveGeofence(childId: String, geofence: Geofence): AppResult<Unit> =
        runCatchingResult {
            val doc = if (geofence.id.isBlank()) geofences(childId).document()
            else geofences(childId).document(geofence.id)
            val data = mapOf(
                "name" to geofence.name,
                "latitude" to geofence.latitude,
                "longitude" to geofence.longitude,
                "radiusMeters" to geofence.radiusMeters.toDouble(),
                "trigger" to geofence.trigger.name,
                "active" to geofence.active,
            )
            doc.set(data).await()
        }

    override suspend fun deleteGeofence(childId: String, geofenceId: String): AppResult<Unit> =
        runCatchingResult { geofences(childId).document(geofenceId).delete().await() }

    override suspend fun reportEvent(childId: String, event: GeofenceEvent): AppResult<Unit> =
        runCatchingResult {
            val data = mapOf(
                "geofenceId" to event.geofenceId,
                "geofenceName" to event.geofenceName,
                "transition" to event.transition.name,
                "latitude" to event.latitude,
                "longitude" to event.longitude,
                "timestampMillis" to event.timestampMillis,
            )
            events(childId).add(data).await()
            Unit
        }

    override fun observeRecentEvents(childId: String, limit: Long): Flow<List<GeofenceEvent>> =
        callbackFlow {
            val registration = events(childId)
                .orderBy("timestampMillis", Query.Direction.DESCENDING)
                .limit(limit)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList()); return@addSnapshotListener
                    }
                    trySend(snapshot?.documents?.map { it.toEvent() } ?: emptyList())
                }
            awaitClose { registration.remove() }
        }

    private fun geofences(childId: String) =
        firestore.collection(CHILDREN).document(childId).collection(GEOFENCES)

    private fun events(childId: String) =
        firestore.collection(CHILDREN).document(childId).collection(EVENTS)

    private fun DocumentSnapshot.toGeofence() = Geofence(
        id = id,
        name = getString("name") ?: "",
        latitude = getDouble("latitude") ?: 0.0,
        longitude = getDouble("longitude") ?: 0.0,
        radiusMeters = (getDouble("radiusMeters") ?: 150.0).toFloat(),
        trigger = getString("trigger")?.let { runCatching { GeofenceTrigger.valueOf(it) }.getOrNull() }
            ?: GeofenceTrigger.ENTER_EXIT,
        active = getBoolean("active") ?: true,
    )

    private fun DocumentSnapshot.toEvent() = GeofenceEvent(
        id = id,
        geofenceId = getString("geofenceId") ?: "",
        geofenceName = getString("geofenceName") ?: "",
        transition = getString("transition")?.let {
            runCatching { GeofenceTransition.valueOf(it) }.getOrNull()
        } ?: GeofenceTransition.ENTER,
        latitude = getDouble("latitude") ?: 0.0,
        longitude = getDouble("longitude") ?: 0.0,
        timestampMillis = getLong("timestampMillis") ?: 0L,
    )

    private companion object {
        const val CHILDREN = "children"
        const val GEOFENCES = "geofences"
        const val EVENTS = "geofenceEvents"
    }
}
