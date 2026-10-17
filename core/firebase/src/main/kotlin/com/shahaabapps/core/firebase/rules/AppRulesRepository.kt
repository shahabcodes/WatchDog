package com.shahaabapps.core.firebase.rules

import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.model.AppRule
import com.shahaabapps.core.model.InstalledApp
import kotlinx.coroutines.flow.Flow

/**
 * App-usage rules and the child's reported app inventory.
 *
 * Firestore layout:
 *  - children/{childId}/appRules/{packageName}      (one doc per ruled app)
 *  - children/{childId}/inventory/installed         (single doc: list of apps)
 */
interface AppRulesRepository {

    /** Rules currently set for a child. */
    fun observeAppRules(childId: String): Flow<List<AppRule>>

    suspend fun saveAppRule(childId: String, rule: AppRule): AppResult<Unit>

    suspend fun deleteAppRule(childId: String, packageName: String): AppResult<Unit>

    /** Child app reports its launchable apps so the parent can pick from them. */
    suspend fun reportInstalledApps(childId: String, apps: List<InstalledApp>): AppResult<Unit>

    /** The child's reported apps (for the parent's picker). */
    fun observeInstalledApps(childId: String): Flow<List<InstalledApp>>

    /** Re-saves any still-plaintext app rules so they become E2E-encrypted (+ hashed id). */
    suspend fun reencryptPlaintext(childId: String)
}
