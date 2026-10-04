package com.shahaabapps.watchdog.feature.usage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.rules.AppRulesRepository
import com.shahaabapps.core.firebase.usage.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class UsageRow(val label: String, val minutes: Int)

data class UsageUiState(
    val rows: List<UsageRow> = emptyList(),
    val totalMinutes: Int = 0,
    val loading: Boolean = true,
)

@HiltViewModel
class UsageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    usageRepository: UsageRepository,
    appRulesRepository: AppRulesRepository,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])
    private val today: String = LocalDate.now().toString()

    val uiState: StateFlow<UsageUiState> = combine(
        usageRepository.observeUsage(childId, today),
        appRulesRepository.observeInstalledApps(childId),
    ) { usage, apps ->
        val labels = apps.associate { it.packageName to it.label }
        // Show only known (user-facing) apps; resolve labels.
        val rows = usage
            .filterKeys { labels.containsKey(it) }
            .map { UsageRow(labels[it.key] ?: it.key, it.value) }
            .filter { it.minutes > 0 }
            .sortedByDescending { it.minutes }
        UsageUiState(rows = rows, totalMinutes = rows.sumOf { it.minutes }, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UsageUiState())
}
