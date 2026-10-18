package com.shahaabapps.core.firebase.child

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.model.BatterySample
import com.shahaabapps.core.model.BatteryStats
import com.shahaabapps.core.model.ChildAccount
import com.shahaabapps.core.model.DeviceInfo
import com.shahaabapps.core.model.DevicePermissions
import com.shahaabapps.core.model.UserProfile
import com.shahaabapps.core.model.UserRole
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.e2ee.E2eeManager
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import javax.inject.Inject
import kotlin.random.Random

class FirebaseChildRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val e2ee: E2eeManager,
    private val authRepository: AuthRepository,
) : ChildRepository {

    override fun observeChild(childId: String): Flow<ChildAccount?> = callbackFlow {
        val viewerUid = authRepository.currentUserIdOrNull()
        e2ee.warmReaderKey(childId, viewerUid)
        val registration = childrenCollection.document(childId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot?.takeIf { it.exists() }?.toChildAccount(viewerUid))
            }
        awaitClose { registration.remove() }
    }

    override suspend fun saveChildProfile(
        childId: String,
        profile: UserProfile,
    ): AppResult<Unit> = runCatchingResult {
        val doc = childrenCollection.document(childId)
        val existing = doc.get().await()
        val code = existing.getString(FIELD_PAIRING_CODE) ?: generatePairingCode()
        @Suppress("UNCHECKED_CAST")
        val linkedParents = existing.get(FIELD_LINKED_PARENTS) as? List<String> ?: emptyList()

        val data = mapOf(
            FIELD_PROFILE to mapOf(
                "name" to profile.name,
                "photoUrl" to profile.photoUrl,
                "dateOfBirthMillis" to profile.dateOfBirthMillis,
            ),
            FIELD_PAIRING_CODE to code,
            FIELD_PAIRING_ACTIVE to linkedParents.isEmpty(),
            FIELD_LINKED_PARENTS to linkedParents,
            FIELD_ROLE to UserRole.CHILD.name,
        )
        doc.set(data, SetOptions.merge()).await()
    }

    override fun observeLinkedChildren(parentId: String): Flow<List<ChildAccount>> = callbackFlow {
        val registration = childrenCollection
            .whereArrayContains(FIELD_LINKED_PARENTS, parentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents.orEmpty()
                // Warm each child's key first so the sealed device fields decrypt.
                launch {
                    docs.forEach { runCatching { e2ee.warmReaderKey(it.id, parentId) } }
                    trySend(docs.mapNotNull { it.takeIf { d -> d.exists() }?.toChildAccount(parentId) })
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun linkChild(
        childId: String,
        code: String,
        parentId: String,
    ): AppResult<Unit> = runCatchingResult {
        // Linking is verified and applied SERVER-SIDE by the linkChild Cloud
        // Function: it checks the pairing code (which security rules can't),
        // confirms the pairing window is open, and adds only the caller
        // (request.auth.uid) to linkedParents. The client therefore no longer
        // reads the child doc or writes linkedParents directly. childId/parentId
        // are resolved server-side from the code and the auth context.
        com.google.firebase.functions.FirebaseFunctions.getInstance()
            .getHttpsCallable("linkChild")
            .call(hashMapOf("code" to code.trim()))
            .await()
        Unit
    }

    override suspend fun unlinkChild(childId: String, parentId: String): AppResult<Unit> =
        runCatchingResult {
            val doc = childrenCollection.document(childId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(doc)
                @Suppress("UNCHECKED_CAST")
                val remaining = (snapshot.get(FIELD_LINKED_PARENTS) as? List<String> ?: emptyList())
                    .filter { it != parentId }
                transaction.update(
                    doc,
                    mapOf(
                        FIELD_LINKED_PARENTS to remaining,
                        FIELD_PAIRING_ACTIVE to remaining.isEmpty(),
                    ),
                )
            }.await()
            Unit
        }

    override suspend fun heartbeat(
        childId: String,
        model: String,
        batteryPercent: Int,
        charging: Boolean,
        drainPerHour: Double,
        temperatureTenthsC: Int,
    ): AppResult<Unit> =
        runCatchingResult {
            // online + lastSeenMillis stay plaintext (the parent derives online/offline
            // from the timestamp's freshness). The rest (model, battery, temperature)
            // is E2E-encrypted; only the parent/child apps read it, no Cloud Function does.
            val secret = JSONObject()
                .put("m", model)
                .put("bp", if (batteryPercent in 1..100) batteryPercent else 0)
                .put("ch", charging)
                .put("dr", if (drainPerHour > 0.0) drainPerHour else 0.0)
                .put("tc", if (temperatureTenthsC > 0) temperatureTenthsC else 0)
                .toString()
            val sealed = e2ee.sealString(childId, childId, secret)
            val device = buildMap<String, Any> {
                put("online", true)
                put("lastSeenMillis", System.currentTimeMillis())
                if (sealed != null) {
                    put("hb", sealed)
                    put("hbEnc", true)
                    // Remove any legacy plaintext fields left by pre-encryption writes.
                    put("model", FieldValue.delete())
                    put("batteryPercent", FieldValue.delete())
                    put("charging", FieldValue.delete())
                    put("batteryDrainPerHour", FieldValue.delete())
                    put("temperatureTenthsC", FieldValue.delete())
                } else {
                    put("model", model)
                    if (batteryPercent in 1..100) put("batteryPercent", batteryPercent)
                    put("charging", charging)
                    if (drainPerHour > 0.0) put("batteryDrainPerHour", drainPerHour)
                    if (temperatureTenthsC > 0) put("temperatureTenthsC", temperatureTenthsC)
                }
            }
            childrenCollection.document(childId).set(
                mapOf(
                    FIELD_DEVICE to device,
                    // Publish whether this child is currently encrypting (all linked
                    // parents wrapped), so security rules can reject plaintext writes
                    // to E2EE collections once active (anti-downgrade). Tracks current
                    // readiness so a just-linked parent in the wrap window isn't blocked.
                    "encActive" to e2ee.childEncryptionReady,
                ),
                SetOptions.merge(),
            ).await()
        }

    override suspend fun updateBatteryStats(childId: String, stats: BatteryStats): AppResult<Unit> =
        runCatchingResult {
            val json = JSONObject()
                .put("s", stats.lastChargeStartMillis)
                .put("e", stats.lastChargeEndMillis)
                .put("f", stats.lastChargeFromPercent)
                .put("tp", stats.lastChargeToPercent)
                .put("h", org.json.JSONArray().apply {
                    stats.hourly.forEach { put(JSONObject().put("t", it.atMillis).put("p", it.percent).put("c", it.tempTenthsC)) }
                })
                .put("th", org.json.JSONArray().apply {
                    stats.temperatureHistory.forEach { put(JSONObject().put("t", it.atMillis).put("c", it.tempTenthsC)) }
                })
                .toString()
            val sealed = e2ee.sealString(childId, childId, json)
            val deviceMap: Map<String, Any> = if (sealed != null) {
                mapOf("statsE" to sealed, "statsEnc" to true, "batteryStats" to FieldValue.delete())
            } else {
                mapOf(
                    "batteryStats" to mapOf(
                        "lastChargeStartMillis" to stats.lastChargeStartMillis,
                        "lastChargeEndMillis" to stats.lastChargeEndMillis,
                        "lastChargeFromPercent" to stats.lastChargeFromPercent,
                        "lastChargeToPercent" to stats.lastChargeToPercent,
                        "hourly" to stats.hourly.map { mapOf("t" to it.atMillis, "p" to it.percent, "c" to it.tempTenthsC) },
                        "tempHistory" to stats.temperatureHistory.map { mapOf("t" to it.atMillis, "c" to it.tempTenthsC) },
                    ),
                )
            }
            childrenCollection.document(childId).set(
                mapOf(FIELD_DEVICE to deviceMap),
                SetOptions.merge(),
            ).await()
        }

    override suspend fun updatePermissions(childId: String, permissions: DevicePermissions): AppResult<Unit> =
        runCatchingResult {
            val json = JSONObject()
                .put("location", permissions.location)
                .put("locationAlways", permissions.locationAlways)
                .put("accessibility", permissions.accessibility)
                .put("usageAccess", permissions.usageAccess)
                .put("notifications", permissions.notifications)
                .put("batteryUnrestricted", permissions.batteryUnrestricted)
                .put("fullScreenAlarm", permissions.fullScreenAlarm)
                .put("storageAccess", permissions.storageAccess)
                .put("reportedAtMillis", permissions.reportedAtMillis)
                .toString()
            val sealed = e2ee.sealString(childId, childId, json)
            val deviceMap: Map<String, Any> = if (sealed != null) {
                mapOf("permsE" to sealed, "permsEnc" to true, "permissions" to FieldValue.delete())
            } else {
                mapOf(
                    "permissions" to mapOf(
                        "location" to permissions.location,
                        "locationAlways" to permissions.locationAlways,
                        "accessibility" to permissions.accessibility,
                        "usageAccess" to permissions.usageAccess,
                        "notifications" to permissions.notifications,
                        "batteryUnrestricted" to permissions.batteryUnrestricted,
                        "fullScreenAlarm" to permissions.fullScreenAlarm,
                        "storageAccess" to permissions.storageAccess,
                        "reportedAtMillis" to permissions.reportedAtMillis,
                    ),
                )
            }
            childrenCollection.document(childId).set(
                mapOf(FIELD_DEVICE to deviceMap),
                SetOptions.merge(),
            ).await()
        }

    private fun DocumentSnapshot.toChildAccount(viewerUid: String?): ChildAccount {
        @Suppress("UNCHECKED_CAST")
        val profileMap = get(FIELD_PROFILE) as? Map<String, Any?> ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val linkedParents = get(FIELD_LINKED_PARENTS) as? List<String> ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val deviceMap = get(FIELD_DEVICE) as? Map<String, Any?> ?: emptyMap()

        return ChildAccount(
            childId = id,
            profile = UserProfile(
                id = id,
                name = profileMap["name"] as? String ?: "",
                photoUrl = profileMap["photoUrl"] as? String,
                dateOfBirthMillis = profileMap["dateOfBirthMillis"] as? Long,
                role = UserRole.CHILD,
            ),
            pairingCode = getString(FIELD_PAIRING_CODE) ?: "",
            linkedParents = linkedParents,
            device = deviceInfo(id, viewerUid, deviceMap),
        )
    }

    /** Builds DeviceInfo, decrypting the sealed heartbeat/permissions/battery fields. */
    private fun deviceInfo(childId: String, viewerUid: String?, deviceMap: Map<String, Any?>): DeviceInfo {
        val online = deviceMap["online"] as? Boolean ?: false
        val lastSeen = deviceMap["lastSeenMillis"] as? Long ?: 0L

        // Heartbeat (model/battery/charging/drain/temp).
        val hb = (deviceMap["hb"] as? String)?.takeIf { deviceMap["hbEnc"] == true }
            ?.let { e2ee.openStringCached(childId, viewerUid, it) }?.let { JSONObject(it) }
        val model = hb?.optString("m") ?: deviceMap["model"] as? String ?: ""
        val batteryPercent = hb?.optInt("bp") ?: (deviceMap["batteryPercent"] as? Long)?.toInt() ?: 0
        val charging = hb?.optBoolean("ch") ?: deviceMap["charging"] as? Boolean ?: false
        val drain = hb?.optDouble("dr") ?: (deviceMap["batteryDrainPerHour"] as? Number)?.toDouble() ?: 0.0
        val temp = hb?.optInt("tc") ?: (deviceMap["temperatureTenthsC"] as? Long)?.toInt() ?: 0

        // Permissions.
        val permsJson = (deviceMap["permsE"] as? String)?.takeIf { deviceMap["permsEnc"] == true }
            ?.let { e2ee.openStringCached(childId, viewerUid, it) }?.let { JSONObject(it) }
        val permissions = when {
            permsJson != null -> DevicePermissions(
                location = permsJson.optBoolean("location"),
                locationAlways = permsJson.optBoolean("locationAlways"),
                accessibility = permsJson.optBoolean("accessibility"),
                usageAccess = permsJson.optBoolean("usageAccess"),
                notifications = permsJson.optBoolean("notifications"),
                batteryUnrestricted = permsJson.optBoolean("batteryUnrestricted"),
                fullScreenAlarm = permsJson.optBoolean("fullScreenAlarm"),
                storageAccess = permsJson.optBoolean("storageAccess"),
                reportedAtMillis = permsJson.optLong("reportedAtMillis"),
            )
            else -> (deviceMap["permissions"] as? Map<*, *>)?.let { p ->
                DevicePermissions(
                    location = p["location"] as? Boolean ?: false,
                    locationAlways = p["locationAlways"] as? Boolean ?: false,
                    accessibility = p["accessibility"] as? Boolean ?: false,
                    usageAccess = p["usageAccess"] as? Boolean ?: false,
                    notifications = p["notifications"] as? Boolean ?: false,
                    batteryUnrestricted = p["batteryUnrestricted"] as? Boolean ?: false,
                    fullScreenAlarm = p["fullScreenAlarm"] as? Boolean ?: false,
                    storageAccess = p["storageAccess"] as? Boolean ?: false,
                    reportedAtMillis = (p["reportedAtMillis"] as? Long) ?: 0L,
                )
            }
        }

        // Battery stats.
        val statsJson = (deviceMap["statsE"] as? String)?.takeIf { deviceMap["statsEnc"] == true }
            ?.let { e2ee.openStringCached(childId, viewerUid, it) }?.let { JSONObject(it) }
        val batteryStats = when {
            statsJson != null -> {
                val h = statsJson.optJSONArray("h")
                val th = statsJson.optJSONArray("th")
                BatteryStats(
                    lastChargeStartMillis = statsJson.optLong("s"),
                    lastChargeEndMillis = statsJson.optLong("e"),
                    lastChargeFromPercent = statsJson.optInt("f"),
                    lastChargeToPercent = statsJson.optInt("tp"),
                    hourly = if (h == null) emptyList() else (0 until h.length()).map {
                        val o = h.getJSONObject(it)
                        BatterySample(o.optLong("t"), o.optInt("p"), o.optInt("c"))
                    },
                    temperatureHistory = if (th == null) emptyList() else (0 until th.length()).map {
                        val o = th.getJSONObject(it)
                        BatterySample(o.optLong("t"), 0, o.optInt("c"))
                    },
                )
            }
            else -> (deviceMap["batteryStats"] as? Map<*, *>)?.let { b ->
                @Suppress("UNCHECKED_CAST")
                val raw = b["hourly"] as? List<Map<String, Any?>> ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val rawTemp = b["tempHistory"] as? List<Map<String, Any?>> ?: emptyList()
                BatteryStats(
                    lastChargeStartMillis = (b["lastChargeStartMillis"] as? Long) ?: 0L,
                    lastChargeEndMillis = (b["lastChargeEndMillis"] as? Long) ?: 0L,
                    lastChargeFromPercent = (b["lastChargeFromPercent"] as? Long)?.toInt() ?: 0,
                    lastChargeToPercent = (b["lastChargeToPercent"] as? Long)?.toInt() ?: 0,
                    hourly = raw.map {
                        BatterySample(
                            atMillis = (it["t"] as? Long) ?: 0L,
                            percent = (it["p"] as? Long)?.toInt() ?: 0,
                            tempTenthsC = (it["c"] as? Long)?.toInt() ?: 0,
                        )
                    },
                    temperatureHistory = rawTemp.map {
                        BatterySample(
                            atMillis = (it["t"] as? Long) ?: 0L,
                            tempTenthsC = (it["c"] as? Long)?.toInt() ?: 0,
                        )
                    },
                )
            }
        }

        return DeviceInfo(
            online = online,
            batteryPercent = batteryPercent,
            model = model,
            lastSeenMillis = lastSeen,
            charging = charging,
            batteryDrainPerHour = drain,
            temperatureTenthsC = temp,
            permissions = permissions,
            batteryStats = batteryStats,
        )
    }

    private fun generatePairingCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no ambiguous chars
        return (1..6).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    }

    private val childrenCollection get() = firestore.collection(COLLECTION_CHILDREN)

    private companion object {
        const val COLLECTION_CHILDREN = "children"
        const val FIELD_PROFILE = "profile"
        const val FIELD_PAIRING_CODE = "pairingCode"
        const val FIELD_PAIRING_ACTIVE = "pairingActive"
        const val FIELD_LINKED_PARENTS = "linkedParents"
        const val FIELD_DEVICE = "device"
        const val FIELD_ROLE = "role"
    }
}
