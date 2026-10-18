package com.shahaabapps.core.common.crypto

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pinnedKeysDataStore: DataStore<Preferences> by preferencesDataStore(name = "pinned_keys")

/**
 * Parent-side record of each child's E2EE public key, captured out-of-band from
 * the pairing QR (which is shown on the physical child device). Pinning this key
 * lets the parent verify the child's identity independently of the backend: even
 * a compromised server can't substitute a different key, because the trusted one
 * came straight off the device's screen.
 *
 * Stored locally only, keyed by childId. Absence means "not yet verified" — the
 * app can then prompt a re-scan.
 */
@Singleton
class PinnedKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun observePinnedKey(childId: String): Flow<String?> =
        context.pinnedKeysDataStore.data.map { it[key(childId)] }

    suspend fun getPinnedKey(childId: String): String? =
        context.pinnedKeysDataStore.data.first()[key(childId)]

    suspend fun pin(childId: String, publicKeyB64: String) {
        context.pinnedKeysDataStore.edit { it[key(childId)] = publicKeyB64 }
    }

    /** Drop the pinned key (e.g. after detecting the child's key changed), so the UI re-prompts verification. */
    suspend fun unpin(childId: String) {
        context.pinnedKeysDataStore.edit { it.remove(key(childId)) }
    }

    // --- trust-on-first-use (TOFU) record of the backend-published child key ---
    // Separate from the QR-pinned key: this is simply the first key we ever saw
    // for a child, so we can detect (and alert on) a later silent key substitution.

    suspend fun getSeenKey(childId: String): String? =
        context.pinnedKeysDataStore.data.first()[seenKey(childId)]

    suspend fun putSeenKey(childId: String, publicKeyB64: String) {
        context.pinnedKeysDataStore.edit { it[seenKey(childId)] = publicKeyB64 }
    }

    private fun key(childId: String) = stringPreferencesKey("child_$childId")
    private fun seenKey(childId: String) = stringPreferencesKey("seen_$childId")
}
