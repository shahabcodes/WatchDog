package com.shahaabapps.watchdog.feature.rules

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.child.ChildRepository
import com.shahaabapps.core.firebase.rules.AppRulesRepository
import com.shahaabapps.core.model.AppRule
import com.shahaabapps.core.model.InstalledApp
import com.shahaabapps.core.model.TimeWindow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppRuleRow(
    val app: InstalledApp,
    val rule: AppRule?,
)

data class ChildDetailUiState(
    val childName: String = "",
    val rows: List<AppRuleRow> = emptyList(),
    val loadingInventory: Boolean = true,
)

@HiltViewModel
class ChildDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appRulesRepository: AppRulesRepository,
    private val childRepository: ChildRepository,
) : ViewModel() {

    val childId: String = checkNotNull(savedStateHandle[ARG_CHILD_ID])

    val uiState: StateFlow<ChildDetailUiState> = combine(
        childRepository.observeChild(childId),
        appRulesRepository.observeInstalledApps(childId),
        appRulesRepository.observeAppRules(childId),
    ) { child, apps, rules ->
        val rulesByPackage = rules.associateBy { it.packageName }
        ChildDetailUiState(
            childName = child?.profile?.name ?: "Child",
            rows = apps.map { app -> AppRuleRow(app, rulesByPackage[app.packageName]) },
            loadingInventory = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChildDetailUiState(),
    )

    fun saveRule(
        app: InstalledApp,
        dailyLimitMinutes: Int?,
        blocked: Boolean,
        schedule: List<TimeWindow>,
    ) {
        viewModelScope.launch {
            val rule = AppRule(
                packageName = app.packageName,
                appLabel = app.label,
                dailyLimitMinutes = dailyLimitMinutes,
                blocked = blocked,
                schedule = schedule,
            )
            appRulesRepository.saveAppRule(childId, rule)
        }
    }

    fun clearRule(packageName: String) {
        viewModelScope.launch { appRulesRepository.deleteAppRule(childId, packageName) }
    }

    companion object {
        const val ARG_CHILD_ID = "childId"
    }
}
