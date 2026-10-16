package com.shahaabapps.watchdog.feature.installguard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.installguard.InstallGuardRepository
import com.shahaabapps.core.firebase.rules.AppRulesRepository
import com.shahaabapps.core.model.InstallGuard
import com.shahaabapps.core.model.InstalledApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InstallGuardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val installGuardRepository: InstallGuardRepository,
    appRulesRepository: AppRulesRepository,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])

    val guard: StateFlow<InstallGuard> = installGuardRepository.observe(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InstallGuard())

    val installedApps: StateFlow<List<InstalledApp>> = appRulesRepository.observeInstalledApps(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun update(transform: (InstallGuard) -> InstallGuard) {
        val next = transform(guard.value)
        viewModelScope.launch { installGuardRepository.setGuard(childId, next) }
    }

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun setAllowlistMode(allowlist: Boolean) = update { it.copy(allowlist = allowlist) }

    fun togglePackage(pkg: String, on: Boolean) = update {
        val set = it.packages.toMutableList()
        if (on) { if (pkg !in set) set.add(pkg) } else set.remove(pkg)
        it.copy(packages = set)
    }
}
