package com.shahaabapps.watchdog.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.common.datastore.ThemePreferences
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.ui.theme.AccentPalette
import com.shahaabapps.core.ui.theme.ThemeMode
import com.shahaabapps.watchdog.util.MapStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferences: ThemePreferences,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeModeName
        .map { runCatching { ThemeMode.valueOf(it) }.getOrDefault(ThemeMode.SYSTEM) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val accentPalette: StateFlow<AccentPalette> = themePreferences.accentPaletteName
        .map { runCatching { AccentPalette.valueOf(it) }.getOrDefault(AccentPalette.GUARDIAN) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccentPalette.GUARDIAN)

    val mapStyle: StateFlow<MapStyle> = themePreferences.mapStyleName
        .map { MapStyle.from(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapStyle.DEFAULT)

    val email: String? = authRepository.currentUserEmail()

    private val _resetMessage = MutableStateFlow<String?>(null)
    /** Set after a reset-email attempt; shown to the user, then cleared. */
    val resetMessage: StateFlow<String?> = _resetMessage.asStateFlow()

    private val _sendingReset = MutableStateFlow(false)
    val sendingReset: StateFlow<Boolean> = _sendingReset.asStateFlow()

    fun sendPasswordReset() {
        val target = email ?: return
        if (_sendingReset.value) return
        _sendingReset.value = true
        viewModelScope.launch {
            _resetMessage.value = when (val result = authRepository.sendPasswordReset(target)) {
                is AppResult.Success -> "Password reset link sent to $target"
                is AppResult.Failure -> result.error.message ?: "Couldn't send the reset email"
            }
            _sendingReset.value = false
        }
    }

    fun clearResetMessage() { _resetMessage.value = null }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { themePreferences.setThemeModeName(mode.name) }
    }

    fun setAccentPalette(palette: AccentPalette) {
        viewModelScope.launch { themePreferences.setAccentPaletteName(palette.name) }
    }

    fun setMapStyle(style: MapStyle) {
        viewModelScope.launch { themePreferences.setMapStyleName(style.name) }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
