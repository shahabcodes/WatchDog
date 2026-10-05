package com.shahaabapps.watchdog.feature.usage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.rules.AppRulesRepository
import com.shahaabapps.core.firebase.usage.UsageRepository
import com.shahaabapps.core.model.AppRule
import com.shahaabapps.core.model.InstalledApp
import com.shahaabapps.core.model.TimeWindow
import com.shahaabapps.core.ui.components.Celebrations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class AppSort(val label: String) {
    USED("Used time"),
    NAME("Name"),
    INSTALLED("Installed on"),
}

/** One app row: the app, today's usage minutes, and its rule (if any). */
data class AppRow(
    val app: InstalledApp,
    val minutes: Int,
    val rule: AppRule?,
)

data class UsageUiState(
    val loading: Boolean = true,
    val totalMinutes: Int = 0,
    val query: String = "",
    val sort: AppSort = AppSort.USED,
    val rows: List<AppRow> = emptyList(),
)

@HiltViewModel
class UsageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    usageRepository: UsageRepository,
    private val appRulesRepository: AppRulesRepository,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])
    private val today: String = LocalDate.now().toString()

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(AppSort.USED)

    val uiState: StateFlow<UsageUiState> = combine(
        usageRepository.observeUsage(childId, today),
        appRulesRepository.observeInstalledApps(childId),
        appRulesRepository.observeAppRules(childId),
        query,
        sort,
    ) { usage, apps, rules, q, selectedSort ->
        val rulesByPackage = rules.associateBy { it.packageName }
        val total = apps.sumOf { usage[it.packageName] ?: 0 }
        var rows = apps.map { app ->
            AppRow(app = app, minutes = usage[app.packageName] ?: 0, rule = rulesByPackage[app.packageName])
        }
        if (q.isNotBlank()) {
            rows = rows.filter { it.app.label.contains(q, ignoreCase = true) }
        }
        rows = when (selectedSort) {
            AppSort.USED -> rows.sortedWith(compareByDescending<AppRow> { it.minutes }.thenBy { it.app.label.lowercase() })
            AppSort.NAME -> rows.sortedBy { it.app.label.lowercase() }
            AppSort.INSTALLED -> rows.sortedByDescending { it.app.installedAtMillis }
        }
        UsageUiState(loading = false, totalMinutes = total, query = q, sort = selectedSort, rows = rows)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UsageUiState())

    fun setQuery(value: String) { query.value = value }
    fun setSort(value: AppSort) { sort.value = value }

    fun saveRule(app: InstalledApp, dailyLimitMinutes: Int?, blocked: Boolean, schedule: List<TimeWindow>) {
        viewModelScope.launch {
            appRulesRepository.saveAppRule(
                childId,
                AppRule(
                    packageName = app.packageName,
                    appLabel = app.label,
                    dailyLimitMinutes = dailyLimitMinutes,
                    blocked = blocked,
                    schedule = schedule,
                ),
            )
            Celebrations.saved()
        }
    }

    fun clearRule(packageName: String) {
        viewModelScope.launch {
            appRulesRepository.deleteAppRule(childId, packageName)
            Celebrations.deleted()
        }
    }
}
