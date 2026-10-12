package com.shahaabapps.core.common.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")

/**
 * Persists the user's appearance choices as plain strings, keeping this module
 * UI-agnostic:
 *  - theme mode: "SYSTEM" / "LIGHT" / "DARK"
 *  - accent palette: "MEADOW" / "BLOSSOM" / "OCEAN"
 * The UI module maps these to its own enums.
 */
@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val themeModeName: Flow<String> = context.themeDataStore.data
        .map { prefs -> prefs[KEY_THEME_MODE] ?: DEFAULT_MODE }

    val accentPaletteName: Flow<String> = context.themeDataStore.data
        .map { prefs -> prefs[KEY_ACCENT_PALETTE] ?: DEFAULT_PALETTE }

    suspend fun setThemeModeName(name: String) {
        context.themeDataStore.edit { prefs -> prefs[KEY_THEME_MODE] = name }
    }

    suspend fun setAccentPaletteName(name: String) {
        context.themeDataStore.edit { prefs -> prefs[KEY_ACCENT_PALETTE] = name }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_ACCENT_PALETTE = stringPreferencesKey("accent_palette")
        const val DEFAULT_MODE = "LIGHT"
        const val DEFAULT_PALETTE = "GUARDIAN"
    }
}
