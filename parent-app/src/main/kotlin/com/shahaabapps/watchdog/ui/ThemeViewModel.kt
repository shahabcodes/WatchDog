package com.shahaabapps.watchdog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.common.datastore.ThemePreferences
import com.shahaabapps.core.ui.theme.AccentPalette
import com.shahaabapps.core.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themePreferences: ThemePreferences,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeModeName
        .map { runCatching { ThemeMode.valueOf(it) }.getOrDefault(ThemeMode.SYSTEM) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val accentPalette: StateFlow<AccentPalette> = themePreferences.accentPaletteName
        .map { runCatching { AccentPalette.valueOf(it) }.getOrDefault(AccentPalette.MEADOW) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccentPalette.MEADOW)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { themePreferences.setThemeModeName(mode.name) }
    }

    fun setAccentPalette(palette: AccentPalette) {
        viewModelScope.launch { themePreferences.setAccentPaletteName(palette.name) }
    }
}
