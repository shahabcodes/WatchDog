package com.shahaabapps.watchdog.feature.protection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.protection.PendingAppsRepository
import com.shahaabapps.core.firebase.rules.AppRulesRepository
import com.shahaabapps.core.model.PendingApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PendingAppsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pendingAppsRepository: PendingAppsRepository,
    private val appRulesRepository: AppRulesRepository,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])

    val pending: StateFlow<List<PendingApp>> = pendingAppsRepository.observe(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Allow the app: remove its block and clear it from review. */
    fun allow(packageName: String) {
        viewModelScope.launch {
            appRulesRepository.deleteAppRule(childId, packageName)
            pendingAppsRepository.remove(childId, packageName)
        }
    }

    /**
     * Keep it blocked: write a parent-owned block rule, then clear it from review.
     * (Shield only holds new apps back while they're pending; app rules are
     * parent-only, so the lasting block must come from here.)
     */
    fun keepBlocked(packageName: String) {
        val label = pending.value.firstOrNull { it.packageName == packageName }?.label ?: packageName
        viewModelScope.launch {
            val saved = appRulesRepository.saveAppRule(
                childId,
                com.shahaabapps.core.model.AppRule(packageName = packageName, appLabel = label, blocked = true),
            )
            if (saved is com.shahaabapps.core.common.AppResult.Success) {
                pendingAppsRepository.remove(childId, packageName)
            }
        }
    }
}
