package com.fms.parentalcontrol.parent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fms.parentalcontrol.core.common.datastore.ThemePreferences
import com.fms.parentalcontrol.core.ui.theme.ThemeMode
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
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM,
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { themePreferences.setThemeModeName(mode.name) }
    }
}
