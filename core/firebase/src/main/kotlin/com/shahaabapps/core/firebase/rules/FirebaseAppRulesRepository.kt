package com.shahaabapps.core.firebase.rules

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.model.AppRule
import com.shahaabapps.core.model.InstalledApp
import com.shahaabapps.core.model.TimeWindow
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseAppRulesRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : AppRulesRepository {

    override fun observeAppRules(childId: String): Flow<List<AppRule>> = callbackFlow {
        val registration = appRulesCollection(childId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.map { it.toAppRule() } ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun saveAppRule(childId: String, rule: AppRule): AppResult<Unit> =
        runCatchingResult {
            val data = mapOf(
                "packageName" to rule.packageName,
                "appLabel" to rule.appLabel,
                "dailyLimitMinutes" to rule.dailyLimitMinutes,
                "blocked" to rule.blocked,
                "schedule" to rule.schedule.map { window ->
                    mapOf(
                        "startMinuteOfDay" to window.startMinuteOfDay,
                        "endMinuteOfDay" to window.endMinuteOfDay,
                        "daysOfWeek" to window.daysOfWeek.toList(),
                    )
                },
            )
            appRulesCollection(childId).document(rule.packageName).set(data).await()
        }

    override suspend fun deleteAppRule(childId: String, packageName: String): AppResult<Unit> =
        runCatchingResult {
            appRulesCollection(childId).document(packageName).delete().await()
        }

    override suspend fun reportInstalledApps(
        childId: String,
        apps: List<InstalledApp>,
    ): AppResult<Unit> = runCatchingResult {
        val data = mapOf(
            "apps" to apps.map {
                buildMap {
                    put("packageName", it.packageName)
                    put("label", it.label)
                    if (it.icon != null) put("icon", it.icon)
                }
            },
            "updatedAtMillis" to System.currentTimeMillis(),
        )
        firestore.collection(COLLECTION_CHILDREN)
            .document(childId)
            .collection(COLLECTION_INVENTORY)
            .document(DOC_INSTALLED)
            .set(data)
            .await()
    }

    override fun observeInstalledApps(childId: String): Flow<List<InstalledApp>> = callbackFlow {
        val registration = firestore.collection(COLLECTION_CHILDREN)
            .document(childId)
            .collection(COLLECTION_INVENTORY)
            .document(DOC_INSTALLED)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                @Suppress("UNCHECKED_CAST")
                val apps = (snapshot?.get("apps") as? List<Map<String, Any?>>).orEmpty().map {
                    InstalledApp(
                        packageName = it["packageName"] as? String ?: "",
                        label = it["label"] as? String ?: "",
                        icon = it["icon"] as? String,
                    )
                }
                trySend(apps)
            }
        awaitClose { registration.remove() }
    }

    private fun appRulesCollection(childId: String) =
        firestore.collection(COLLECTION_CHILDREN).document(childId).collection(COLLECTION_APP_RULES)

    private fun DocumentSnapshot.toAppRule(): AppRule {
        @Suppress("UNCHECKED_CAST")
        val scheduleRaw = get("schedule") as? List<Map<String, Any?>> ?: emptyList()
        val schedule = scheduleRaw.map { window ->
            @Suppress("UNCHECKED_CAST")
            val days = (window["daysOfWeek"] as? List<Long>)?.map { it.toInt() }?.toSet()
                ?: setOf(1, 2, 3, 4, 5, 6, 7)
            TimeWindow(
                startMinuteOfDay = (window["startMinuteOfDay"] as? Long)?.toInt() ?: 0,
                endMinuteOfDay = (window["endMinuteOfDay"] as? Long)?.toInt() ?: 1440,
                daysOfWeek = days,
            )
        }
        return AppRule(
            packageName = getString("packageName") ?: id,
            appLabel = getString("appLabel") ?: "",
            dailyLimitMinutes = (get("dailyLimitMinutes") as? Long)?.toInt(),
            schedule = schedule,
            blocked = getBoolean("blocked") ?: false,
        )
    }

    private companion object {
        const val COLLECTION_CHILDREN = "children"
        const val COLLECTION_APP_RULES = "appRules"
        const val COLLECTION_INVENTORY = "inventory"
        const val DOC_INSTALLED = "installed"
    }
}
