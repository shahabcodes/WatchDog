package com.shahaabapps.core.firebase.geofence

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.e2ee.E2eeManager
import com.shahaabapps.core.model.GeoPoint
import com.shahaabapps.core.model.Geofence
import com.shahaabapps.core.model.GeofenceEvent
import com.shahaabapps.core.model.GeofenceShape
import com.shahaabapps.core.model.GeofenceTransition
import com.shahaabapps.core.model.GeofenceTrigger
import android.content.Context
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class FirebaseGeofenceRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val e2ee: E2eeManager,
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context,
) : GeofenceRepository {

    override fun observeGeofences(childId: String): Flow<List<Geofence>> = callbackFlow {
        val viewerUid = authRepository.currentUserIdOrNull()
        e2ee.warmReaderKey(childId, viewerUid)
        val registration = geofences(childId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList()); return@addSnapshotListener
            }
            trySend(snapshot?.documents?.mapNotNull { it.toGeofence(childId, viewerUid) } ?: emptyList())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun saveGeofence(childId: String, geofence: Geofence): AppResult<Unit> =
        runCatchingResult {
            val doc = if (geofence.id.isBlank()) geofences(childId).document()
            else geofences(childId).document(geofence.id)
            // Non-sensitive fields stay plaintext so the child's enforcement and the
            // geofence Cloud Function can still read trigger/active without a key.
            val base = mapOf(
                "radiusMeters" to geofence.radiusMeters.toDouble(),
                "trigger" to geofence.trigger.name,
                "active" to geofence.active,
                "shape" to geofence.shape.name,
                "icon" to geofence.icon,
            )
            val uid = authRepository.currentUserIdOrNull()
            val secret = JSONObject()
                .put("name", geofence.name)
                .put("lat", geofence.latitude)
                .put("lng", geofence.longitude)
                .put("pts", JSONArray().apply {
                    geofence.points.forEach { put(JSONArray().put(it.latitude).put(it.longitude)) }
                })
                .toString()
            val sealed = e2ee.sealString(childId, uid, secret)
            val data = if (sealed != null) {
                base + mapOf("e" to sealed, "enc" to true)
            } else {
                base + mapOf(
                    "name" to geofence.name,
                    "latitude" to geofence.latitude,
                    "longitude" to geofence.longitude,
                    "points" to geofence.points.map { mapOf("latitude" to it.latitude, "longitude" to it.longitude) },
                )
            }
            doc.set(data).await()
        }

    override suspend fun deleteGeofence(childId: String, geofenceId: String): AppResult<Unit> =
        runCatchingResult { geofences(childId).document(geofenceId).delete().await() }

    override suspend fun clearEvents(childId: String): AppResult<Unit> =
        runCatchingResult {
            // Firestore has no "delete collection"; page through and batch-delete.
            while (true) {
                val snap = events(childId).limit(400).get().await()
                if (snap.isEmpty) break
                val batch = firestore.batch()
                snap.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
                if (snap.size() < 400) break
            }
        }

    override suspend fun reportEvent(childId: String, event: GeofenceEvent): AppResult<Unit> =
        runCatchingResult {
            // geofenceId/transition/timestamp stay plaintext so the Cloud Function
            // can honour the geofence's trigger/active and skip stale events. The
            // place name + coordinates are encrypted, so the push stays content-free.
            val base = mapOf(
                "geofenceId" to event.geofenceId,
                "transition" to event.transition.name,
                "timestampMillis" to event.timestampMillis,
            )
            val uid = authRepository.currentUserIdOrNull()
            val secret = JSONObject()
                .put("name", event.geofenceName)
                .put("lat", event.latitude)
                .put("lng", event.longitude)
                .toString()
            val sealed = e2ee.sealString(childId, uid, secret)
            val data = if (sealed != null) {
                base + mapOf("e" to sealed, "enc" to true)
            } else {
                base + mapOf(
                    "geofenceName" to event.geofenceName,
                    "latitude" to event.latitude,
                    "longitude" to event.longitude,
                )
            }
            events(childId).add(data).await()
            Unit
        }

    override fun observeRecentEvents(childId: String, limit: Long): Flow<List<GeofenceEvent>> =
        callbackFlow {
            val viewerUid = authRepository.currentUserIdOrNull()
            e2ee.warmReaderKey(childId, viewerUid)
            val registration = events(childId)
                .orderBy("timestampMillis", Query.Direction.DESCENDING)
                .limit(limit)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList()); return@addSnapshotListener
                    }
                    trySend(snapshot?.documents?.map { it.toEvent(childId, viewerUid) } ?: emptyList())
                }
            awaitClose { registration.remove() }
        }

    override suspend fun getEvent(childId: String, eventId: String): GeofenceEvent? {
        val viewerUid = authRepository.currentUserIdOrNull()
        e2ee.warmReaderKey(childId, viewerUid)
        return runCatching {
            val doc = events(childId).document(eventId).get().await()
            if (doc.exists()) doc.toEvent(childId, viewerUid) else null
        }.getOrNull()
    }

    override suspend fun reencryptPlaintext(childId: String) {
        val viewerUid = authRepository.currentUserIdOrNull()
        runCatching {
            val snap = geofences(childId).get().await()
            snap.documents.forEach { doc ->
                if (doc.getBoolean("enc") == true) return@forEach
                val gf = doc.toGeofence(childId, viewerUid) ?: return@forEach
                saveGeofence(childId, gf) // re-saves sealed to the same doc id
            }
        }
    }

    private fun geofences(childId: String) =
        firestore.collection(CHILDREN).document(childId).collection(GEOFENCES)

    private fun events(childId: String) =
        firestore.collection(CHILDREN).document(childId).collection(EVENTS)

    private fun DocumentSnapshot.toGeofence(childId: String, viewerUid: String?): Geofence? {
        val common = { name: String, lat: Double, lng: Double, points: List<GeoPoint> ->
            // Cache the decrypted name so the background geofence push can name the
            // place by id without needing to decrypt in a cold process.
            GeofenceNameCache.put(context, childId, id, name)
            Geofence(
                id = id,
                name = name,
                latitude = lat,
                longitude = lng,
                radiusMeters = (getDouble("radiusMeters") ?: 150.0).toFloat(),
                trigger = getString("trigger")?.let { runCatching { GeofenceTrigger.valueOf(it) }.getOrNull() }
                    ?: GeofenceTrigger.ENTER_EXIT,
                active = getBoolean("active") ?: true,
                shape = getString("shape")?.let { runCatching { GeofenceShape.valueOf(it) }.getOrNull() }
                    ?: GeofenceShape.CIRCLE,
                points = points,
                icon = getString("icon") ?: "place",
            )
        }
        val sealed = getString("e")
        if (getBoolean("enc") == true && sealed != null) {
            // Encrypted; drop the place if the key isn't available rather than
            // surface a nameless (0,0) geofence.
            val json = e2ee.openStringCached(childId, viewerUid, sealed)?.let { JSONObject(it) }
                ?: return null
            val pts = json.optJSONArray("pts")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val p = arr.getJSONArray(i)
                    GeoPoint(latitude = p.getDouble(0), longitude = p.getDouble(1))
                }
            } ?: emptyList()
            return common(json.optString("name"), json.optDouble("lat", 0.0), json.optDouble("lng", 0.0), pts)
        }
        // Plaintext places are accepted on read; forged plaintext writes are blocked
        // at the server (rules require enc once the child is encrypting).
        @Suppress("UNCHECKED_CAST")
        val rawPoints = get("points") as? List<Map<String, Any?>> ?: emptyList()
        val points = rawPoints.map {
            GeoPoint(
                latitude = (it["latitude"] as? Number)?.toDouble() ?: 0.0,
                longitude = (it["longitude"] as? Number)?.toDouble() ?: 0.0,
            )
        }
        return common(getString("name") ?: "", getDouble("latitude") ?: 0.0, getDouble("longitude") ?: 0.0, points)
    }

    private fun DocumentSnapshot.toEvent(childId: String, viewerUid: String?): GeofenceEvent {
        val transition = getString("transition")?.let {
            runCatching { GeofenceTransition.valueOf(it) }.getOrNull()
        } ?: GeofenceTransition.ENTER
        val sealed = getString("e")
        val (name, lat, lng) = if (getBoolean("enc") == true && sealed != null) {
            val json = e2ee.openStringCached(childId, viewerUid, sealed)?.let { JSONObject(it) }
            Triple(
                json?.optString("name") ?: "",
                json?.optDouble("lat", 0.0) ?: 0.0,
                json?.optDouble("lng", 0.0) ?: 0.0,
            )
        } else {
            Triple(getString("geofenceName") ?: "", getDouble("latitude") ?: 0.0, getDouble("longitude") ?: 0.0)
        }
        return GeofenceEvent(
            id = id,
            geofenceId = getString("geofenceId") ?: "",
            geofenceName = name,
            transition = transition,
            latitude = lat,
            longitude = lng,
            timestampMillis = getLong("timestampMillis") ?: 0L,
        )
    }

    private companion object {
        const val CHILDREN = "children"
        const val GEOFENCES = "geofences"
        const val EVENTS = "geofenceEvents"
    }
}
