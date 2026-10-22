package com.shahaabapps.core.firebase.child

import com.shahaabapps.core.firebase.callSafely

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
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import javax.inject.Inject

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
        com.shahaabapps.core.common.Diag.action("child.saveChildProfile", "childId=$childId")
        // Only the profile: linkedParents and pairing are server-managed (the
        // rules reject client writes to them).
        val data = mapOf(
            FIELD_PROFILE to mapOf(
                "name" to profile.name,
                "photoUrl" to profile.photoUrl,
                "dateOfBirthMillis" to profile.dateOfBirthMillis,
            ),
            FIELD_ROLE to UserRole.CHILD.name,
        )
        childrenCollection.document(childId).set(data, SetOptions.merge()).await()
    }

    override suspend fun startPairing(childId: String?): AppResult<PairingCode> = runCatchingResult {
        com.shahaabapps.core.common.Diag.action("child.startPairing", "childId=$childId")
        val args = hashMapOf<String, Any>()
        if (childId != null) args["childId"] = childId
        val data = functions.getHttpsCallable("startPairing").callSafely(name = "startPairing", data = args).getData() as? Map<*, *>
        PairingCode(
            code = requireNotNull(data?.get("code") as? String) { "No pairing code returned" },
            expiresAtMillis = (data?.get("expiresAtMillis") as? Number)?.toLong() ?: 0L,
        )
    }

    override fun observeParentRequests(childId: String): Flow<List<ParentRequest>> = callbackFlow {
        val registration = childrenCollection.document(childId).collection("parentRequests")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.documents.orEmpty().map { d ->
                        ParentRequest(
                            parentId = d.id,
                            email = d.getString("email").orEmpty(),
                            expiresAtMillis = d.getTimestamp("expiresAt")?.toDate()?.time ?: 0L,
                        )
                    },
                )
            }
        awaitClose { registration.remove() }
    }

    override suspend fun linkedParentInfo(childId: String?): AppResult<List<ParentInfo>> = runCatchingResult {
        val args = hashMapOf<String, Any>()
        if (childId != null) args["childId"] = childId
        val data = functions.getHttpsCallable("linkedParentInfo").callSafely(name = "linkedParentInfo", data = args).getData() as? Map<*, *>
        (data?.get("parents") as? List<*>).orEmpty().mapNotNull { p ->
            val m = p as? Map<*, *> ?: return@mapNotNull null
            ParentInfo(uid = m["uid"] as? String ?: return@mapNotNull null, email = m["email"] as? String ?: "")
        }
    }

    override suspend fun respondToParentRequest(parentId: String, approve: Boolean): AppResult<Unit> =
        runCatchingResult {
            com.shahaabapps.core.common.Diag.action("child.respondToParentRequest", "parentId=$parentId approve=$approve")
            functions.getHttpsCallable("respondToParentRequest")
                .callSafely(name = "respondToParentRequest", data = hashMapOf("parentId" to parentId, "approve" to approve))
                
            Unit
        }

    override suspend fun requestParentRemoval(childId: String, parentId: String): AppResult<Unit> =
        runCatchingResult {
            com.shahaabapps.core.common.Diag.action("child.requestParentRemoval", "childId=$childId parentId=$parentId")
            functions.getHttpsCallable("requestParentRemoval")
                .callSafely(name = "requestParentRemoval", data = hashMapOf("childId" to childId, "parentId" to parentId))
            Unit
        }

    override fun observeParentRemovals(childId: String): Flow<List<ParentRemoval>> = callbackFlow {
        val registration = childrenCollection.document(childId).collection("parentRemovals")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val now = System.currentTimeMillis()
                trySend(
                    snapshot?.documents.orEmpty().map { d ->
                        ParentRemoval(
                            parentId = d.id,
                            email = d.getString("email").orEmpty(),
                            requestedByEmail = d.getString("requestedByEmail").orEmpty(),
                            expiresAtMillis = d.getTimestamp("expiresAt")?.toDate()?.time ?: 0L,
                        )
                    }.filter { it.expiresAtMillis > now },
                )
            }
        awaitClose { registration.remove() }
    }

    override suspend fun respondToParentRemoval(parentId: String, approve: Boolean): AppResult<Unit> =
        runCatchingResult {
            com.shahaabapps.core.common.Diag.action("child.respondToParentRemoval", "parentId=$parentId approve=$approve")
            functions.getHttpsCallable("respondToParentRemoval")
                .callSafely(name = "respondToParentRemoval", data = hashMapOf("parentId" to parentId, "approve" to approve))
            Unit
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

    override suspend fun linkChild(code: String): AppResult<LinkOutcome> = runCatchingResult {
        com.shahaabapps.core.common.Diag.action("child.linkChild")
        // Linking needs a verified email. If it was verified since sign-in, the
        // token still says otherwise until it is refreshed.
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.let { user ->
            if (!user.isEmailVerified) {
                runCatching { user.reload().await() }
                if (user.isEmailVerified) runCatching { user.getIdToken(true).await() }
            }
        }
        // The linkChild Cloud Function redeems the single-use code and asks the
        // child's phone to approve this parent; nothing is linked until it does.
        val data = functions.getHttpsCallable("linkChild")
            .callSafely(name = "linkChild", data = hashMapOf("code" to code.trim()))
            .getData() as? Map<*, *>
        val childId = requireNotNull(data?.get("childId") as? String) { "No child returned" }
        if (data?.get("status") == "linked") LinkOutcome.Linked(childId) else LinkOutcome.AwaitingApproval(childId)
    }

    override fun observeRestoreToken(installId: String): Flow<String?> = callbackFlow {
        val registration = firestore.collection("restoreTokens").document(installId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val expires = snapshot?.getTimestamp("expiresAt")?.toDate()?.time ?: 0L
                trySend(snapshot?.getString("token")?.takeIf { expires > System.currentTimeMillis() })
            }
        awaitClose { registration.remove() }
    }

    override suspend fun completeRestore(tempChildId: String): AppResult<Unit> = runCatchingResult {
        com.shahaabapps.core.common.Diag.action("child.completeRestore")
        functions.getHttpsCallable("completeRestore").callSafely(name = "completeRestore", data = hashMapOf("tempChildId" to tempChildId))
        Unit
    }

    override suspend fun unlinkChild(childId: String): AppResult<Unit> =
        runCatchingResult {
            com.shahaabapps.core.common.Diag.action("child.unlinkChild", "childId=$childId")
            functions.getHttpsCallable("unlinkChild").callSafely(name = "unlinkChild", data = hashMapOf("childId" to childId))
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
            val sealed = e2ee.sealString(childId, childId, secret, slot = "device/hb")
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
            val sealed = e2ee.sealString(childId, childId, json, slot = "device/stats")
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
                .put("powerSaveMode", permissions.powerSaveMode)
                .put("locationServicesOff", permissions.locationServicesOff)
                .put("backgroundRestricted", permissions.backgroundRestricted)
                .put("reportedAtMillis", permissions.reportedAtMillis)
                .toString()
            val sealed = e2ee.sealString(childId, childId, json, slot = "device/perms")
            // Plaintext on/off flags for the tamper-alert Cloud Function, which
            // can't decrypt permsE: just whether each protection is on.
            val guard = mapOf(
                "accessibility" to permissions.accessibility,
                "usageAccess" to permissions.usageAccess,
                "admin" to permissions.adminActive,
                "clockOk" to permissions.clockOk,
                "gpsGenuine" to permissions.gpsGenuine,
                "parentKeysOk" to permissions.parentKeysOk,
                "adbOff" to permissions.adbOff,
                // This Shield reads payloads bound to their document (slot).
                "aad2" to true,
                "tz" to permissions.phoneZone,
                "homeTz" to permissions.homeZone,
            )
            val deviceMap: Map<String, Any> = if (sealed != null) {
                mapOf("permsE" to sealed, "permsEnc" to true, "permissions" to FieldValue.delete(), "guard" to guard)
            } else {
                mapOf(
                    "guard" to guard,
                    "permissions" to mapOf(
                        "location" to permissions.location,
                        "locationAlways" to permissions.locationAlways,
                        "accessibility" to permissions.accessibility,
                        "usageAccess" to permissions.usageAccess,
                        "notifications" to permissions.notifications,
                        "batteryUnrestricted" to permissions.batteryUnrestricted,
                        "fullScreenAlarm" to permissions.fullScreenAlarm,
                        "storageAccess" to permissions.storageAccess,
                        "powerSaveMode" to permissions.powerSaveMode,
                        "locationServicesOff" to permissions.locationServicesOff,
                        "backgroundRestricted" to permissions.backgroundRestricted,
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
            ?.let { e2ee.openStringCached(childId, viewerUid, it, slot = "device/hb") }?.let { JSONObject(it) }
        val model = hb?.optString("m") ?: deviceMap["model"] as? String ?: ""
        val batteryPercent = hb?.optInt("bp") ?: (deviceMap["batteryPercent"] as? Long)?.toInt() ?: 0
        val charging = hb?.optBoolean("ch") ?: deviceMap["charging"] as? Boolean ?: false
        val drain = hb?.optDouble("dr") ?: (deviceMap["batteryDrainPerHour"] as? Number)?.toDouble() ?: 0.0
        val temp = hb?.optInt("tc") ?: (deviceMap["temperatureTenthsC"] as? Long)?.toInt() ?: 0

        // Permissions.
        val permsJson = (deviceMap["permsE"] as? String)?.takeIf { deviceMap["permsEnc"] == true }
            ?.let { e2ee.openStringCached(childId, viewerUid, it, slot = "device/perms") }?.let { JSONObject(it) }
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
                powerSaveMode = permsJson.optBoolean("powerSaveMode"),
                locationServicesOff = permsJson.optBoolean("locationServicesOff"),
                backgroundRestricted = permsJson.optBoolean("backgroundRestricted"),
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
                    powerSaveMode = p["powerSaveMode"] as? Boolean ?: false,
                    locationServicesOff = p["locationServicesOff"] as? Boolean ?: false,
                    backgroundRestricted = p["backgroundRestricted"] as? Boolean ?: false,
                    reportedAtMillis = (p["reportedAtMillis"] as? Long) ?: 0L,
                )
            }
        }

        // Battery stats.
        val statsJson = (deviceMap["statsE"] as? String)?.takeIf { deviceMap["statsEnc"] == true }
            ?.let { e2ee.openStringCached(childId, viewerUid, it, slot = "device/stats") }?.let { JSONObject(it) }
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
            // Shield reports these protection flags only in the plaintext guard map
            // (the encrypted permissions leave them out), so take them from there.
            permissions = permissions?.let { p ->
                val g = deviceMap["guard"] as? Map<*, *> ?: return@let p
                p.copy(
                    adminActive = g["admin"] as? Boolean ?: p.adminActive,
                    clockOk = g["clockOk"] as? Boolean ?: p.clockOk,
                    gpsGenuine = g["gpsGenuine"] as? Boolean ?: p.gpsGenuine,
                    parentKeysOk = g["parentKeysOk"] as? Boolean ?: p.parentKeysOk,
                    adbOff = g["adbOff"] as? Boolean ?: p.adbOff,
                )
            },
            batteryStats = batteryStats,
            guard = (deviceMap["guard"] as? Map<*, *>)?.also { g ->
                e2ee.noteChildReadsSlots(childId, g["aad2"] == true)
            }?.let { g ->
                com.shahaabapps.core.model.DeviceGuard(
                    accessibility = g["accessibility"] as? Boolean ?: true,
                    usageAccess = g["usageAccess"] as? Boolean ?: true,
                    admin = g["admin"] as? Boolean ?: true,
                    clockOk = g["clockOk"] as? Boolean ?: true,
                    gpsGenuine = g["gpsGenuine"] as? Boolean ?: true,
                    phoneZone = g["tz"] as? String ?: "",
                    homeZone = g["homeTz"] as? String ?: "",
                )
            } ?: com.shahaabapps.core.model.DeviceGuard(),
        )
    }

    private val functions get() = FirebaseFunctions.getInstance()

    private val childrenCollection get() = firestore.collection(COLLECTION_CHILDREN)

    private companion object {
        const val COLLECTION_CHILDREN = "children"
        const val FIELD_PROFILE = "profile"
        const val FIELD_PAIRING_CODE = "pairingCode"
        const val FIELD_LINKED_PARENTS = "linkedParents"
        const val FIELD_DEVICE = "device"
        const val FIELD_ROLE = "role"
    }
}
