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
        val viewerUid = authRepository.currentUserIdOrNull()
        e2ee.warmReaderKey(childId, viewerUid)
        val registration = appRulesCollection(childId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                // Dedupe by package: a rule may briefly exist both as a legacy
                // plaintext-id doc and as the new hashed-id encrypted doc.
                val rules = snapshot?.documents?.mapNotNull { it.toAppRule(childId, viewerUid) }.orEmpty()
                    .associateBy { it.packageName }.values.toList()
                trySend(rules)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun saveAppRule(childId: String, rule: AppRule): AppResult<Unit> =
        runCatchingResult {
            // The rule's package, label, limits and schedule are sensitive. Seal them
            // and store under a HASHED doc id so the package name isn't in the path
            // either. updatedAtMillis stays plaintext (a non-sensitive timestamp the
            // child compares when overriding a grant). Plaintext fallback (legacy id)
            // when the key isn't ready.
            val uid = authRepository.currentUserIdOrNull()
            val json = JSONObject()
                .put("p", rule.packageName)
                .put("l", rule.appLabel)
                .put("dl", rule.dailyLimitMinutes ?: -1)
                .put("b", rule.blocked)
                .put(
                    "sch",
                    JSONArray().apply {
                        rule.schedule.forEach { w ->
                            put(
                                JSONObject()
                                    .put("s", w.startMinuteOfDay)
                                    .put("e", w.endMinuteOfDay)
                                    .put("d", JSONArray(w.daysOfWeek.sorted())),
                            )
                        }
                    },
                )
                .toString()
            val sealed = e2ee.sealString(childId, uid, json)
            if (sealed != null) {
                appRulesCollection(childId).document(ruleDocId(rule.packageName)).set(
                    mapOf("e" to sealed, "enc" to true, "updatedAtMillis" to System.currentTimeMillis()),
                ).await()
                // Remove any legacy plaintext doc keyed by the raw package name.
                runCatching { appRulesCollection(childId).document(rule.packageName).delete().await() }
            } else {
                appRulesCollection(childId).document(rule.packageName).set(
                    mapOf(
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
                    ),
                ).await()
            }
        }

    override suspend fun deleteAppRule(childId: String, packageName: String): AppResult<Unit> =
        runCatchingResult {
            // Delete both the hashed and any legacy plaintext-id doc for this package.
            runCatching { appRulesCollection(childId).document(ruleDocId(packageName)).delete().await() }
            runCatching { appRulesCollection(childId).document(packageName).delete().await() }
            Unit
        }

    override suspend fun reencryptPlaintext(childId: String) {
        val viewerUid = authRepository.currentUserIdOrNull()
        runCatching {
            val snap = appRulesCollection(childId).get().await()
            snap.documents.forEach { doc ->
                if (doc.getBoolean("enc") == true) return@forEach
                val rule = doc.toAppRule(childId, viewerUid) ?: return@forEach
                // Re-save: writes the sealed rule under the hashed id and deletes the
                // legacy plaintext-id doc.
                saveAppRule(childId, rule)
            }
        }
    }

    /** Stable, non-reversible document id for a rule so the package isn't in the path. */
    private fun ruleDocId(packageName: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(packageName.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
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

    private fun DocumentSnapshot.toAppRule(childId: String, viewerUid: String?): AppRule? {
        val updatedAt = (get("updatedAtMillis") as? Long) ?: 0L
        val sealed = getString("e")
        if (getBoolean("enc") == true && sealed != null) {
            val json = e2ee.openStringCached(childId, viewerUid, sealed)?.let { JSONObject(it) } ?: return null
            val schArr = json.optJSONArray("sch")
            val schedule = if (schArr == null) emptyList() else (0 until schArr.length()).map { i ->
                val w = schArr.getJSONObject(i)
                val d = w.optJSONArray("d")
                val days = if (d == null) setOf(1, 2, 3, 4, 5, 6, 7)
                else (0 until d.length()).map { d.getInt(it) }.toSet()
                TimeWindow(
                    startMinuteOfDay = w.optInt("s", 0),
                    endMinuteOfDay = w.optInt("e", 1440),
                    daysOfWeek = days,
                )
            }
            return AppRule(
                packageName = json.optString("p"),
                appLabel = json.optString("l"),
                dailyLimitMinutes = json.optInt("dl", -1).takeIf { it >= 0 },
                schedule = schedule,
                blocked = json.optBoolean("b", false),
                updatedAtMillis = updatedAt,
            )
        }
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
            updatedAtMillis = updatedAt,
        )
    }

    private companion object {
        const val COLLECTION_CHILDREN = "children"
        const val COLLECTION_APP_RULES = "appRules"
        const val COLLECTION_INVENTORY = "inventory"
        const val DOC_INSTALLED = "installed"
    }
}
