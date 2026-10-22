package com.shahaabapps.core.firebase.device

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.e2ee.E2eeManager
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
    private val e2ee: E2eeManager,
    private val authRepository: AuthRepository,
) : DeviceRepository {

    override suspend fun updateLocation(childId: String, location: ChildLocation): AppResult<Unit> =
        runCatchingResult {
            val sealed = e2ee.sealCoords(childId, location.latitude, location.longitude, slot = "live/location")
            val data = if (sealed != null) {
                mapOf(
                    "e" to sealed,
                    "enc" to true,
                    "accuracyMeters" to location.accuracyMeters.toDouble(),
                    "timestampMillis" to location.timestampMillis,
                )
            } else {
                mapOf(
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "accuracyMeters" to location.accuracyMeters.toDouble(),
                    "timestampMillis" to location.timestampMillis,
                )
            }
            live(childId).document(LOCATION).set(data).await()
        }

    override fun observeLocation(childId: String): Flow<ChildLocation?> = callbackFlow {
        // Warm this child's data key so encrypted coordinates decrypt synchronously
        // in the listener below.
        authRepository.currentUserIdOrNull()?.let { e2ee.warmDataKey(childId, it) }
        val registration = live(childId).document(LOCATION).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(null); return@addSnapshotListener
            }
            trySend(snapshot?.takeIf { it.exists() }?.toLocation(childId))
        }
        awaitClose { registration.remove() }
    }

    override suspend fun sendRing(childId: String, message: String): AppResult<Unit> =
        runCatchingResult {
            com.shahaabapps.core.common.Diag.action("device.sendRing", "childId=$childId")
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
            com.shahaabapps.core.common.Diag.action("device.clearRing", "childId=$childId")
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
            val sealed = e2ee.sealCoords(childId, stop.latitude, stop.longitude, slot = "travelHistory")
            val data = if (sealed != null) {
                mapOf(
                    "e" to sealed,
                    "enc" to true,
                    "arrivalMillis" to stop.arrivalMillis,
                    "departureMillis" to stop.departureMillis,
                )
            } else {
                mapOf(
                    "latitude" to stop.latitude,
                    "longitude" to stop.longitude,
                    "arrivalMillis" to stop.arrivalMillis,
                    "departureMillis" to stop.departureMillis,
                )
            }
            travelHistory(childId).add(data).await()
            Unit
        }

    override fun observeTravelHistory(childId: String, limit: Long): Flow<List<TravelStop>> =
        callbackFlow {
            authRepository.currentUserIdOrNull()?.let { e2ee.warmDataKey(childId, it) }
            val registration = travelHistory(childId)
                .orderBy("arrivalMillis", Query.Direction.DESCENDING)
                .limit(limit)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList()); return@addSnapshotListener
                    }
                    trySend(snapshot?.documents?.mapNotNull { it.toTravelStop(childId) } ?: emptyList())
                }
            awaitClose { registration.remove() }
        }

    override suspend fun appendTrailPoint(childId: String, date: String, point: TrailPoint): AppResult<Unit> =
        runCatchingResult {
            // Merge-set with arrayUnion so the day's doc is created on first point.
            trail(childId).document(date)
                .set(mapOf("points" to FieldValue.arrayUnion(trailEntry(childId, date, point))), SetOptions.merge())
                .await()
        }

    override suspend fun appendTrailPoints(childId: String, date: String, points: List<TrailPoint>): AppResult<Unit> =
        runCatchingResult {
            if (points.isEmpty()) return@runCatchingResult Unit
            val entries = points.map { trailEntry(childId, date, it) }.toTypedArray()
            trail(childId).document(date)
                .set(mapOf("points" to FieldValue.arrayUnion(*entries)), SetOptions.merge())
                .await()
        }

    /** A trail array entry: encrypted (`e` + `t`) when possible, else plaintext (`lat`/`lng`/`t`). */
    private fun trailEntry(childId: String, date: String, point: TrailPoint): Map<String, Any> {
        val sealed = e2ee.sealCoords(childId, point.latitude, point.longitude, slot = "trail/$date")
        return if (sealed != null) {
            mapOf("e" to sealed, "t" to point.timestampMillis)
        } else {
            mapOf("lat" to point.latitude, "lng" to point.longitude, "t" to point.timestampMillis)
        }
    }

    override fun observeTrail(childId: String, date: String): Flow<List<TrailPoint>> = callbackFlow {
        authRepository.currentUserIdOrNull()?.let { e2ee.warmDataKey(childId, it) }
        val registration = trail(childId).document(date).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList()); return@addSnapshotListener
            }
            @Suppress("UNCHECKED_CAST")
            val raw = snapshot?.get("points") as? List<Map<String, Any?>> ?: emptyList()
            val points = raw.mapNotNull { entry ->
                val t = (entry["t"] as? Number)?.toLong() ?: 0L
                val sealed = entry["e"] as? String
                if (sealed != null) {
                    // Encrypted point; skip if the key isn't available yet rather
                    // than plot a bogus (0,0) coordinate.
                    val coords = e2ee.openCoordsCached(childId, sealed, slot = "trail/$date") ?: return@mapNotNull null
                    TrailPoint(latitude = coords.first, longitude = coords.second, timestampMillis = t)
                } else {
                    // Trail points are child-write-only and not covered by the
                    // re-encryption migrator, so pre-E2EE plaintext history is kept.
                    TrailPoint(
                        latitude = (entry["lat"] as? Number)?.toDouble() ?: 0.0,
                        longitude = (entry["lng"] as? Number)?.toDouble() ?: 0.0,
                        timestampMillis = t,
                    )
                }
            }.sortedBy { it.timestampMillis }
            trySend(points)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun requestLocation(childId: String): AppResult<Unit> = runCatchingResult {
        com.shahaabapps.core.common.Diag.action("device.requestLocation", "childId=$childId")
        firestore.collection(CHILDREN).document(childId)
            .collection(COMMANDS).document(LOCATION)
            .set(mapOf("nonce" to System.currentTimeMillis())).await()
        Unit
    }

    override suspend fun requestPermission(childId: String, permission: String): AppResult<Unit> = runCatchingResult {
        com.shahaabapps.core.common.Diag.action("device.requestPermission", "childId=$childId permission=$permission")
        firestore.collection(CHILDREN).document(childId)
            .collection(COMMANDS).document("permissionRequest")
            .set(
                mapOf(
                    "permission" to permission,
                    "nonce" to System.currentTimeMillis(),
                    "requestedAtMillis" to System.currentTimeMillis(),
                ),
            ).await()
        Unit
    }

    override suspend fun updateLocationStatus(
        childId: String,
        status: com.shahaabapps.core.model.LocationStatus,
    ): AppResult<Unit> = runCatchingResult {
        live(childId).document(LOCATION_STATUS).set(
            mapOf(
                "state" to status.state,
                "lastOutcome" to status.lastOutcome,
                "lastOutcomeMillis" to status.lastOutcomeMillis,
                "lastFixMillis" to status.lastFixMillis,
                "lastPublishMillis" to status.lastPublishMillis,
                "lastAccuracyMeters" to status.lastAccuracyMeters.toDouble(),
                "updatedAtMillis" to status.updatedAtMillis,
            ),
        ).await()
        Unit
    }

    override fun observeLocationStatus(childId: String): Flow<com.shahaabapps.core.model.LocationStatus?> = callbackFlow {
        val registration = live(childId).document(LOCATION_STATUS).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(null); return@addSnapshotListener
            }
            trySend(
                com.shahaabapps.core.model.LocationStatus(
                    state = snapshot.getString("state") ?: "",
                    lastOutcome = snapshot.getString("lastOutcome") ?: "",
                    lastOutcomeMillis = snapshot.getLong("lastOutcomeMillis") ?: 0L,
                    lastFixMillis = snapshot.getLong("lastFixMillis") ?: 0L,
                    lastPublishMillis = snapshot.getLong("lastPublishMillis") ?: 0L,
                    lastAccuracyMeters = (snapshot.getDouble("lastAccuracyMeters") ?: 0.0).toFloat(),
                    updatedAtMillis = snapshot.getLong("updatedAtMillis") ?: 0L,
                ),
            )
        }
        awaitClose { registration.remove() }
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

    private fun DocumentSnapshot.toTravelStop(childId: String): TravelStop? {
        val sealed = getString("e")
        val coords = if (sealed != null) {
            e2ee.openCoordsCached(childId, sealed, slot = "travelHistory") ?: return null
        } else {
            // Travel stops are child-write-only and not covered by the migrator, so
            // pre-E2EE plaintext history is kept.
            (getDouble("latitude") ?: 0.0) to (getDouble("longitude") ?: 0.0)
        }
        return TravelStop(
            id = id,
            latitude = coords.first,
            longitude = coords.second,
            arrivalMillis = getLong("arrivalMillis") ?: 0L,
            departureMillis = getLong("departureMillis") ?: 0L,
        )
    }

    private fun DocumentSnapshot.toLocation(childId: String): ChildLocation? {
        val sealed = getString("e")
        val coords = if (sealed != null) {
            e2ee.openCoordsCached(childId, sealed, slot = "live/location") ?: return null
        } else {
            // Accept plaintext: the child writes it whenever it isn't yet able to
            // encrypt (key exchange still settling). Availability of the child's
            // location must not depend on the encryption state having caught up.
            (getDouble("latitude") ?: 0.0) to (getDouble("longitude") ?: 0.0)
        }
        return ChildLocation(
            latitude = coords.first,
            longitude = coords.second,
            accuracyMeters = (getDouble("accuracyMeters") ?: 0.0).toFloat(),
            timestampMillis = getLong("timestampMillis") ?: 0L,
        )
    }

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
        const val LOCATION_STATUS = "locationStatus"
        const val RING = "ring"
        const val TRAVEL_HISTORY = "travelHistory"
        const val TRAIL = "trail"
    }
}
