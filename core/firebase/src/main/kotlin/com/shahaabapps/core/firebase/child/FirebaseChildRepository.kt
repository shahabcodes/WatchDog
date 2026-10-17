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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlin.random.Random

class FirebaseChildRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ChildRepository {

    override fun observeChild(childId: String): Flow<ChildAccount?> = callbackFlow {
        val registration = childrenCollection.document(childId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot?.takeIf { it.exists() }?.toChildAccount())
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
                trySend(snapshot?.documents?.mapNotNull { it.toChildAccount() } ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    override fun observeViewableChildren(viewerId: String): Flow<List<ChildAccount>> = callbackFlow {
        val registration = childrenCollection
            .whereArrayContains(FIELD_VIEWERS, viewerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toChildAccount() } ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun linkChild(
        childId: String,
        code: String,
        parentId: String,
    ): AppResult<Unit> = runCatchingResult {
        val doc = childrenCollection.document(childId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(doc)
            require(snapshot.exists()) { "No child found for this code." }
            val active = snapshot.getBoolean(FIELD_PAIRING_ACTIVE) ?: false
            val storedCode = snapshot.getString(FIELD_PAIRING_CODE)
            require(active) { "This code is no longer active." }
            require(storedCode != null && storedCode.equals(code, ignoreCase = true)) {
                "The code doesn't match. Check and try again."
            }
            transaction.update(doc, FIELD_LINKED_PARENTS, FieldValue.arrayUnion(parentId))
        }.await()
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
            val device = buildMap<String, Any> {
                put("online", true)
                put("lastSeenMillis", System.currentTimeMillis())
                put("model", model)
                if (batteryPercent in 1..100) put("batteryPercent", batteryPercent)
                put("charging", charging)
                if (drainPerHour > 0.0) put("batteryDrainPerHour", drainPerHour)
                if (temperatureTenthsC > 0) put("temperatureTenthsC", temperatureTenthsC)
            }
            childrenCollection.document(childId).set(
                mapOf(FIELD_DEVICE to device),
                SetOptions.merge(),
            ).await()
        }

    override suspend fun updateBatteryStats(childId: String, stats: BatteryStats): AppResult<Unit> =
        runCatchingResult {
            val map = mapOf(
                "lastChargeStartMillis" to stats.lastChargeStartMillis,
                "lastChargeEndMillis" to stats.lastChargeEndMillis,
                "lastChargeFromPercent" to stats.lastChargeFromPercent,
                "lastChargeToPercent" to stats.lastChargeToPercent,
                "hourly" to stats.hourly.map { mapOf("t" to it.atMillis, "p" to it.percent, "c" to it.tempTenthsC) },
                "tempHistory" to stats.temperatureHistory.map { mapOf("t" to it.atMillis, "c" to it.tempTenthsC) },
            )
            childrenCollection.document(childId).set(
                mapOf(FIELD_DEVICE to mapOf("batteryStats" to map)),
                SetOptions.merge(),
            ).await()
        }

    override suspend fun updatePermissions(childId: String, permissions: DevicePermissions): AppResult<Unit> =
        runCatchingResult {
            val map = mapOf(
                "location" to permissions.location,
                "locationAlways" to permissions.locationAlways,
                "accessibility" to permissions.accessibility,
                "usageAccess" to permissions.usageAccess,
                "notifications" to permissions.notifications,
                "batteryUnrestricted" to permissions.batteryUnrestricted,
                "fullScreenAlarm" to permissions.fullScreenAlarm,
                "storageAccess" to permissions.storageAccess,
                "reportedAtMillis" to permissions.reportedAtMillis,
            )
            childrenCollection.document(childId).set(
                mapOf(FIELD_DEVICE to mapOf("permissions" to map)),
                SetOptions.merge(),
            ).await()
        }

    private fun DocumentSnapshot.toChildAccount(): ChildAccount {
        @Suppress("UNCHECKED_CAST")
        val profileMap = get(FIELD_PROFILE) as? Map<String, Any?> ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val linkedParents = get(FIELD_LINKED_PARENTS) as? List<String> ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val viewers = get(FIELD_VIEWERS) as? List<String> ?: emptyList()
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
            viewers = viewers,
            device = DeviceInfo(
                online = deviceMap["online"] as? Boolean ?: false,
                batteryPercent = (deviceMap["batteryPercent"] as? Long)?.toInt() ?: 0,
                model = deviceMap["model"] as? String ?: "",
                lastSeenMillis = deviceMap["lastSeenMillis"] as? Long ?: 0L,
                charging = deviceMap["charging"] as? Boolean ?: false,
                batteryDrainPerHour = (deviceMap["batteryDrainPerHour"] as? Number)?.toDouble() ?: 0.0,
                temperatureTenthsC = (deviceMap["temperatureTenthsC"] as? Long)?.toInt() ?: 0,
                permissions = (deviceMap["permissions"] as? Map<*, *>)?.let { p ->
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
                },
                batteryStats = (deviceMap["batteryStats"] as? Map<*, *>)?.let { b ->
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
                },
            ),
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
        const val FIELD_VIEWERS = "viewers"
        const val FIELD_DEVICE = "device"
        const val FIELD_ROLE = "role"
    }
}
