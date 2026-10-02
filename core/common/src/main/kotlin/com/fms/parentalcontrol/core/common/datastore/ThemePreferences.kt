package com.fms.parentalcontrol.core.common.datastore

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
 * Persists the user's selected theme mode as a plain string ("SYSTEM"/"LIGHT"/"DARK").
 * The UI module maps this to its `ThemeMode` enum, so this module stays UI-agnostic.
 */
@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val themeModeName: Flow<String> = context.themeDataStore.data
        .map { prefs -> prefs[KEY_THEME_MODE] ?: DEFAULT }

    suspend fun setThemeModeName(name: String) {
        context.themeDataStore.edit { prefs -> prefs[KEY_THEME_MODE] = name }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        const val DEFAULT = "SYSTEM"
    }
}
