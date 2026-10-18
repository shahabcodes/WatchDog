package com.shahaabapps.core.firebase.e2ee

import android.content.Context
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.geofence.GeofenceRepository
import com.shahaabapps.core.firebase.rules.AppRulesRepository
import com.shahaabapps.core.firebase.usage.UsageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time backfill that re-encrypts data written before E2EE was enabled
 * ("encrypt new data only"). Runs on the parent (which holds the key and can
 * re-seal; the child reads it with the same shared key), once per child, and
 * only once this device can actually encrypt for that child. The low-churn,
 * slow-to-self-heal categories are covered: saved places, app rules and past
 * usage days. Inventory + device fields self-heal on the child's next write.
 */
@Singleton
class E2eeMigrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val e2ee: E2eeManager,
    private val authRepository: AuthRepository,
    private val geofenceRepository: GeofenceRepository,
    private val appRulesRepository: AppRulesRepository,
    private val usageRepository: UsageRepository,
) {
    suspend fun runIfNeeded(childId: String) {
        val uid = authRepository.currentUserIdOrNull() ?: return
        if (isDone(uid, childId)) {
            // Already migrated (possibly before the cutover flag existed) — ensure
            // the downgrade guard is enabled for this child.
            e2ee.markEncryptionCutover(childId)
            return
        }
        // Probe: skip (and retry on a later launch) until the key is ready, so we
        // don't rewrite docs as plaintext again.
        e2ee.warmReaderKey(childId, uid)
        if (e2ee.sealString(childId, uid, "probe") == null) return
        geofenceRepository.reencryptPlaintext(childId)
        appRulesRepository.reencryptPlaintext(childId)
        usageRepository.reencryptPlaintext(childId)
        // All pre-E2EE plaintext for this child is now re-encrypted, so readers may
        // safely refuse any future plaintext (downgrade/forgery) for it.
        e2ee.markEncryptionCutover(childId)
        markDone(uid, childId)
    }

    private fun prefs() = context.getSharedPreferences("e2ee_migration", Context.MODE_PRIVATE)
    private fun key(uid: String, childId: String) = "$uid/$childId"
    private fun isDone(uid: String, childId: String): Boolean =
        runCatching { prefs().getBoolean(key(uid, childId), false) }.getOrDefault(false)
    private fun markDone(uid: String, childId: String) {
        runCatching { prefs().edit().putBoolean(key(uid, childId), true).apply() }
    }
}
