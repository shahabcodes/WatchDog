package com.shahaabapps.shield.feature.kid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.focus.FocusRepository
import com.shahaabapps.core.firebase.rules.AppRulesRepository
import com.shahaabapps.core.firebase.usage.UsageRepository
import com.shahaabapps.core.model.FocusSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** One app's screen time today, for the child's "My time" list. */
data class KidApp(
    val label: String,
    val iconBase64: String?,
    val minutes: Int,
    val limitMinutes: Int?,
    val blocked: Boolean,
)

data class KidDay(val label: String, val minutes: Int, val isToday: Boolean)

data class KidUiState(
    val totalMinutes: Int = 0,
    val apps: List<KidApp> = emptyList(),
    val weekly: List<KidDay> = emptyList(),
    val focus: FocusSettings = FocusSettings(),
    val loading: Boolean = true,
)

@HiltViewModel
class KidViewModel @Inject constructor(
    authRepository: AuthRepository,
    usageRepository: UsageRepository,
    appRulesRepository: AppRulesRepository,
    focusRepository: FocusRepository,
) : ViewModel() {

    private val childId: String? = authRepository.currentUserIdOrNull()
    private val today: LocalDate = LocalDate.now()

    val uiState: StateFlow<KidUiState> =
        if (childId == null) {
            flowOf(KidUiState(loading = false))
        } else {
            combine(
                usageRepository.observeUsage(childId, today.toString()),
                appRulesRepository.observeInstalledApps(childId),
                appRulesRepository.observeAppRules(childId),
                usageRepository.observeDailyTotals(childId, today.minusDays(6).toString()),
                focusRepository.observe(childId),
            ) { usage, apps, rules, totals, focus ->
                val known = apps.map { it.packageName }.toSet()
                val total = usage.filterKeys { it in known }.values.sum()
                val ruleByPkg = rules.associateBy { it.packageName }
                val appList = apps.mapNotNull { app ->
                    val m = usage[app.packageName] ?: 0
                    val rule = ruleByPkg[app.packageName]
                    if (m > 0 || rule?.blocked == true || rule?.dailyLimitMinutes != null) {
                        KidApp(
                            label = app.label.ifBlank { app.packageName },
                            iconBase64 = app.icon,
                            minutes = m,
                            limitMinutes = rule?.dailyLimitMinutes,
                            blocked = rule?.blocked == true,
                        )
                    } else {
                        null
                    }
                }.sortedByDescending { it.minutes }
                val week = (0L..6L).map { offset ->
                    val date = today.minusDays(6 - offset)
                    KidDay(
                        label = date.dayOfWeek.name.take(1),
                        minutes = totals[date.toString()] ?: 0,
                        isToday = date == today,
                    )
                }
                KidUiState(total, appList, week, focus, loading = false)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KidUiState())
}
