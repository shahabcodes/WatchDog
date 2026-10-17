package com.shahaabapps.core.firebase.rules

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.runCatchingResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.e2ee.E2eeManager
import com.shahaabapps.core.model.AppRule
import com.shahaabapps.core.model.InstalledApp
import com.shahaabapps.core.model.TimeWindow
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class FirebaseAppRulesRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val e2ee: E2eeManager,
    private val authRepository: AuthRepository,
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
                "updatedAtMillis" to System.currentTimeMillis(),
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
        // The app list (names + icons) reveals exactly what's on the child's phone,
        // so E2E-encrypt the whole payload; only the parent/child apps read it. Fall
        // back to plaintext if the key isn't ready yet.
        val uid = authRepository.currentUserIdOrNull()
        val json = JSONArray().apply {
            apps.forEach {
                put(
                    JSONObject()
                        .put("p", it.packageName)
                        .put("l", it.label)
                        .put("i", it.icon ?: "")
                        .put("t", it.installedAtMillis),
                )
            }
        }.toString()
        val sealed = e2ee.sealString(childId, uid, json)
        val data: Map<String, Any> = if (sealed != null) {
            mapOf("e" to sealed, "enc" to true, "updatedAtMillis" to System.currentTimeMillis())
        } else {
            mapOf(
                "apps" to apps.map { app ->
                    buildMap<String, Any> {
                        put("packageName", app.packageName)
                        put("label", app.label)
                        app.icon?.let { put("icon", it) }
                        put("installedAt", app.installedAtMillis)
                    }
                },
                "updatedAtMillis" to System.currentTimeMillis(),
            )
        }
        firestore.collection(COLLECTION_CHILDREN)
            .document(childId)
            .collection(COLLECTION_INVENTORY)
            .document(DOC_INSTALLED)
            .set(data)
            .await()
    }

    override fun observeInstalledApps(childId: String): Flow<List<InstalledApp>> = callbackFlow {
        val viewerUid = authRepository.currentUserIdOrNull()
        e2ee.warmReaderKey(childId, viewerUid)
        val registration = firestore.collection(COLLECTION_CHILDREN)
            .document(childId)
            .collection(COLLECTION_INVENTORY)
            .document(DOC_INSTALLED)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val apps = snapshot?.toInstalledApps(childId, viewerUid) ?: emptyList()
                trySend(apps)
            }
        awaitClose { registration.remove() }
    }

    private fun DocumentSnapshot.toInstalledApps(childId: String, viewerUid: String?): List<InstalledApp> {
        val sealed = getString("e")
        if (getBoolean("enc") == true && sealed != null) {
            val json = e2ee.openStringCached(childId, viewerUid, sealed)?.let { JSONArray(it) } ?: return emptyList()
            return (0 until json.length()).map { i ->
                val o = json.getJSONObject(i)
                InstalledApp(
                    packageName = o.optString("p"),
                    label = o.optString("l"),
                    icon = o.optString("i").ifBlank { null },
                    installedAtMillis = o.optLong("t"),
                )
            }
        }
        @Suppress("UNCHECKED_CAST")
        return (get("apps") as? List<Map<String, Any?>>).orEmpty().map {
            InstalledApp(
                packageName = it["packageName"] as? String ?: "",
                label = it["label"] as? String ?: "",
                icon = it["icon"] as? String,
                installedAtMillis = (it["installedAt"] as? Number)?.toLong() ?: 0L,
            )
        }
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
            updatedAtMillis = (get("updatedAtMillis") as? Long) ?: 0L,
        )
    }

    private companion object {
        const val COLLECTION_CHILDREN = "children"
        const val COLLECTION_APP_RULES = "appRules"
        const val COLLECTION_INVENTORY = "inventory"
        const val DOC_INSTALLED = "installed"
    }
}
