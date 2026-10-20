package com.shahaabapps.core.firebase.focus

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.e2ee.E2eeManager
import com.shahaabapps.core.model.FocusSettings
import com.shahaabapps.core.model.FocusWindow
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * Focus schedules (Bedtime / School) that auto-block apps on the child device.
 *
 * Firestore layout: `children/{childId}/settings/focus`. The parent writes it from
 * Watch Dog; the child (Shield) reads it to enforce blocking during active windows.
 * The settings (which include the allowed-app lists) are E2E-encrypted — only the
 * parent/child apps (both key holders) read them; no Cloud Function does.
 */
interface FocusRepository {
    fun observe(childId: String): Flow<FocusSettings>
    suspend fun save(childId: String, settings: FocusSettings): AppResult<Unit>
}

class FirebaseFocusRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val e2ee: E2eeManager,
    private val authRepository: AuthRepository,
) : FocusRepository {

    override fun observe(childId: String): Flow<FocusSettings> = callbackFlow {
        val viewerUid = authRepository.currentUserIdOrNull()
        e2ee.warmReaderKey(childId, viewerUid)
        val registration = doc(childId).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(FocusSettings())
                return@addSnapshotListener
            }
            trySend(snapshot.decode(childId, viewerUid))
        }
        awaitClose { registration.remove() }
    }

    override suspend fun save(childId: String, settings: FocusSettings): AppResult<Unit> =
        runCatchingResult {
            val uid = authRepository.currentUserIdOrNull()
            val json = JSONObject()
                .put("bedtime", settings.bedtime.toJson())
                .put("school", settings.school.toJson())
                .toString()
            val sealed = e2ee.sealString(childId, uid, json, slot = "settings/focus")
            val data: Map<String, Any> = if (sealed != null) {
                mapOf("e" to sealed, "enc" to true, "updatedAtMillis" to System.currentTimeMillis())
            } else {
                mapOf(
                    "bedtime" to settings.bedtime.toMap(),
                    "school" to settings.school.toMap(),
                    "updatedAtMillis" to System.currentTimeMillis(),
                )
            }
            doc(childId).set(data).await()
            Unit
        }

    private fun doc(childId: String) =
        firestore.collection("children").document(childId)
            .collection("settings").document("focus")

    private fun DocumentSnapshot.decode(childId: String, viewerUid: String?): FocusSettings {
        val sealed = getString("e")
        if (getBoolean("enc") == true && sealed != null) {
            val json = e2ee.openStringCached(childId, viewerUid, sealed, slot = "settings/focus")?.let { JSONObject(it) }
                ?: return FocusSettings()
            return FocusSettings(
                bedtime = json.optJSONObject("bedtime").toWindow(FocusSettings().bedtime),
                school = json.optJSONObject("school").toWindow(FocusSettings().school),
            )
        }
        return FocusSettings(
            bedtime = window("bedtime", FocusSettings().bedtime),
            school = window("school", FocusSettings().school),
        )
    }

    private fun FocusWindow.toJson(): JSONObject = JSONObject()
        .put("enabled", enabled)
        .put("startMinute", startMinute)
        .put("endMinute", endMinute)
        .put("days", JSONArray(days.sorted()))
        .put("allowedPackages", JSONArray(allowedPackages.sorted()))

    private fun JSONObject?.toWindow(fallback: FocusWindow): FocusWindow {
        if (this == null) return fallback
        val daysArr = optJSONArray("days")
        val days = if (daysArr == null) null else (0 until daysArr.length()).map { daysArr.getInt(it) }.toSet()
        val apArr = optJSONArray("allowedPackages")
        val allowed = if (apArr == null) emptySet() else (0 until apArr.length()).map { apArr.getString(it) }.toSet()
        return FocusWindow(
            enabled = optBoolean("enabled", false),
            startMinute = optInt("startMinute", fallback.startMinute),
            endMinute = optInt("endMinute", fallback.endMinute),
            days = if (days.isNullOrEmpty()) fallback.days else days,
            allowedPackages = allowed,
        )
    }

    private fun FocusWindow.toMap(): Map<String, Any> = mapOf(
        "enabled" to enabled,
        "startMinute" to startMinute,
        "endMinute" to endMinute,
        "days" to days.sorted().map { it.toLong() },
        "allowedPackages" to allowedPackages.sorted(),
    )

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.window(key: String, fallback: FocusWindow): FocusWindow {
        val raw = get(key) as? Map<String, Any> ?: return fallback
        val days = (raw["days"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.toSet()
        val allowed = (raw["allowedPackages"] as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()
        return FocusWindow(
            enabled = raw["enabled"] as? Boolean ?: false,
            startMinute = (raw["startMinute"] as? Number)?.toInt() ?: fallback.startMinute,
            endMinute = (raw["endMinute"] as? Number)?.toInt() ?: fallback.endMinute,
            days = if (days.isNullOrEmpty()) fallback.days else days,
            allowedPackages = allowed,
        )
    }
}
