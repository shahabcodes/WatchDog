package com.shahaabapps.core.firebase.focus

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.model.FocusSettings
import com.shahaabapps.core.model.FocusWindow
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Focus schedules (Bedtime / School) that auto-block apps on the child device.
 *
 * Firestore layout: `children/{childId}/settings/focus`. The parent writes it from
 * Watch Dog; the child (Shield) reads it to enforce blocking during active windows.
 */
interface FocusRepository {
    fun observe(childId: String): Flow<FocusSettings>
    suspend fun save(childId: String, settings: FocusSettings): AppResult<Unit>
}

class FirebaseFocusRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : FocusRepository {

    override fun observe(childId: String): Flow<FocusSettings> = callbackFlow {
        val registration = doc(childId).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(FocusSettings())
                return@addSnapshotListener
            }
            trySend(
                FocusSettings(
                    bedtime = snapshot.window("bedtime", FocusSettings().bedtime),
                    school = snapshot.window("school", FocusSettings().school),
                ),
            )
        }
        awaitClose { registration.remove() }
    }

    override suspend fun save(childId: String, settings: FocusSettings): AppResult<Unit> =
        runCatchingResult {
            doc(childId).set(
                mapOf(
                    "bedtime" to settings.bedtime.toMap(),
                    "school" to settings.school.toMap(),
                    "updatedAtMillis" to System.currentTimeMillis(),
                ),
            ).await()
            Unit
        }

    private fun doc(childId: String) =
        firestore.collection("children").document(childId)
            .collection("settings").document("focus")

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
