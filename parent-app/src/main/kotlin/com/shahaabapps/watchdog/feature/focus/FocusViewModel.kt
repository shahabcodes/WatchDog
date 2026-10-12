package com.shahaabapps.watchdog.feature.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.focus.FocusRepository
import com.shahaabapps.core.firebase.rules.AppRulesRepository
import com.shahaabapps.core.model.FocusSettings
import com.shahaabapps.core.model.InstalledApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FocusViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val focusRepository: FocusRepository,
    appRulesRepository: AppRulesRepository,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])

    val settings: StateFlow<FocusSettings> = focusRepository.observe(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusSettings())

    /** Installed apps on the child device, for the per-window allowlist picker. */
    val installedApps: StateFlow<List<InstalledApp>> =
        appRulesRepository.observeInstalledApps(childId)
            .map { apps -> apps.sortedBy { it.label.lowercase() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(settings: FocusSettings) {
        viewModelScope.launch {
            focusRepository.save(childId, settings)
        }
    }
}
