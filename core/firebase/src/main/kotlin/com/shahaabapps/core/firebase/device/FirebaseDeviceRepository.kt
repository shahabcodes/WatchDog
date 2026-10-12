package com.shahaabapps.core.firebase.device

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.model.ChildLocation
import com.shahaabapps.core.model.RingCommand
import com.shahaabapps.core.model.TrailPoint
import com.shahaabapps.core.model.TravelStop
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

class FirebaseDeviceRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : DeviceRepository {

    override suspend fun updateLocation(childId: String, location: ChildLocation): AppResult<Unit> =
        runCatchingResult {
            val data = mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "accuracyMeters" to location.accuracyMeters.toDouble(),
                "timestampMillis" to location.timestampMillis,
            )
            live(childId).document(LOCATION).set(data).await()
        }

    override fun observeLocation(childId: String): Flow<ChildLocation?> = callbackFlow {
        val registration = live(childId).document(LOCATION).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(null); return@addSnapshotListener
            }
            trySend(snapshot?.takeIf { it.exists() }?.toLocation())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun sendRing(childId: String, message: String): AppResult<Unit> =
        runCatchingResult {
            val data = mapOf(
                "active" to true,
                "message" to message,
                "nonce" to UUID.randomUUID().toString(),
                "requestedAtMillis" to System.currentTimeMillis(),
            )
            live(childId).document(RING).set(data).await()
        }

    override suspend fun clearRing(childId: String): AppResult<Unit> =
        runCatchingResult {
            live(childId).document(RING).update("active", false).await()
        }

    override fun observeRing(childId: String): Flow<RingCommand?> = callbackFlow {
        val registration = live(childId).document(RING).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(null); return@addSnapshotListener
            }
            trySend(snapshot?.takeIf { it.exists() }?.toRing())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun addTravelStop(childId: String, stop: TravelStop): AppResult<Unit> =
        runCatchingResult {
            val data = mapOf(
                "latitude" to stop.latitude,
                "longitude" to stop.longitude,
                "arrivalMillis" to stop.arrivalMillis,
                "departureMillis" to stop.departureMillis,
            )
            travelHistory(childId).add(data).await()
            Unit
        }

    override fun observeTravelHistory(childId: String, limit: Long): Flow<List<TravelStop>> =
        callbackFlow {
            val registration = travelHistory(childId)
                .orderBy("arrivalMillis", Query.Direction.DESCENDING)
                .limit(limit)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList()); return@addSnapshotListener
                    }
                    trySend(snapshot?.documents?.map { it.toTravelStop() } ?: emptyList())
                }
            awaitClose { registration.remove() }
        }

    override suspend fun appendTrailPoint(childId: String, date: String, point: TrailPoint): AppResult<Unit> =
        runCatchingResult {
            val entry = mapOf(
                "lat" to point.latitude,
                "lng" to point.longitude,
                "t" to point.timestampMillis,
            )
            // Merge-set with arrayUnion so the day's doc is created on first point.
            trail(childId).document(date)
                .set(mapOf("points" to FieldValue.arrayUnion(entry)), SetOptions.merge())
                .await()
        }

    override fun observeTrail(childId: String, date: String): Flow<List<TrailPoint>> = callbackFlow {
        val registration = trail(childId).document(date).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList()); return@addSnapshotListener
            }
            @Suppress("UNCHECKED_CAST")
            val raw = snapshot?.get("points") as? List<Map<String, Any?>> ?: emptyList()
            val points = raw.map {
                TrailPoint(
                    latitude = (it["lat"] as? Number)?.toDouble() ?: 0.0,
                    longitude = (it["lng"] as? Number)?.toDouble() ?: 0.0,
                    timestampMillis = (it["t"] as? Number)?.toLong() ?: 0L,
                )
            }.sortedBy { it.timestampMillis }
            trySend(points)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun requestLocation(childId: String): AppResult<Unit> = runCatchingResult {
        firestore.collection(CHILDREN).document(childId)
            .collection(COMMANDS).document(LOCATION)
            .set(mapOf("nonce" to System.currentTimeMillis())).await()
        Unit
    }

    override fun observeLocationRequests(childId: String): Flow<Long> = callbackFlow {
        val registration = firestore.collection(CHILDREN).document(childId)
            .collection(COMMANDS).document(LOCATION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(0L); return@addSnapshotListener
                }
                trySend(snapshot?.getLong("nonce") ?: 0L)
            }
        awaitClose { registration.remove() }
    }

    private fun live(childId: String) =
        firestore.collection(CHILDREN).document(childId).collection(LIVE)

    private fun trail(childId: String) =
        firestore.collection(CHILDREN).document(childId).collection(TRAIL)

    private fun travelHistory(childId: String) =
        firestore.collection(CHILDREN).document(childId).collection(TRAVEL_HISTORY)

    private fun DocumentSnapshot.toTravelStop() = TravelStop(
        id = id,
        latitude = getDouble("latitude") ?: 0.0,
        longitude = getDouble("longitude") ?: 0.0,
        arrivalMillis = getLong("arrivalMillis") ?: 0L,
        departureMillis = getLong("departureMillis") ?: 0L,
    )

    private fun DocumentSnapshot.toLocation() = ChildLocation(
        latitude = getDouble("latitude") ?: 0.0,
        longitude = getDouble("longitude") ?: 0.0,
        accuracyMeters = (getDouble("accuracyMeters") ?: 0.0).toFloat(),
        timestampMillis = getLong("timestampMillis") ?: 0L,
    )

    private fun DocumentSnapshot.toRing() = RingCommand(
        active = getBoolean("active") ?: false,
        message = getString("message") ?: "",
        nonce = getString("nonce") ?: "",
        requestedAtMillis = getLong("requestedAtMillis") ?: 0L,
    )

    private companion object {
        const val CHILDREN = "children"
        const val LIVE = "live"
        const val COMMANDS = "commands"
        const val LOCATION = "location"
        const val RING = "ring"
        const val TRAVEL_HISTORY = "travelHistory"
        const val TRAIL = "trail"
    }
}
