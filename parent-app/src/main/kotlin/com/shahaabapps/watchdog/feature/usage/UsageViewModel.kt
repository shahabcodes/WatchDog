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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class AppSort(val label: String) {
    USED("Used time"),
    NAME("Name"),
    INSTALLED("Installed on"),
}

/** Which apps the list shows. */
enum class AppFilter(val label: String) {
    ALL("All apps"),
    RESTRICTED("Restricted"),
    BLOCKED("Blocked"),
    SUPERVISED("Supervised"),
}

/**
 * One app row: the app, today's usage minutes, and its rule (if any). [newApp]: just
 * installed and awaiting review, so Shield blocks it ([newAppsBlocked]) or
 * supervises it until the parent decides.
 */
data class AppRow(
    val app: InstalledApp,
    val minutes: Int,
    val rule: AppRule?,
    val newApp: Boolean = false,
    val newAppsBlocked: Boolean = false,
) {
    /** Awaiting review with no rule of its own yet. */
    val reviewing: Boolean get() = rule == null && newApp
    val blocked: Boolean get() = rule?.blocked == true || (reviewing && newAppsBlocked)
    val supervised: Boolean get() = !blocked && (rule?.watched == true || reviewing)
    /** A daily limit or allowed / blocked hours (not blocked outright). */
    val restricted: Boolean get() = !blocked && rule != null && (rule.dailyLimitMinutes != null || rule.schedule.isNotEmpty())
}

data class UsageUiState(
    val loading: Boolean = true,
    val totalMinutes: Int = 0,
    val query: String = "",
    val sort: AppSort = AppSort.USED,
    val filter: AppFilter = AppFilter.ALL,
    val rows: List<AppRow> = emptyList(),
    /** How many apps are watched, and their screenshots on this phone from today. */
    val watchedApps: Int = 0,
    val watchShotsToday: Int = 0,
)

@HiltViewModel
class UsageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    usageRepository: UsageRepository,
    private val appRulesRepository: AppRulesRepository,
    private val pendingAppsRepository: com.shahaabapps.core.firebase.protection.PendingAppsRepository,
    protectionRepository: com.shahaabapps.core.firebase.protection.ProtectionRepository,
    watchShotSync: com.shahaabapps.watchdog.feature.watch.WatchShotSync,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val authRepository: com.shahaabapps.core.firebase.auth.AuthRepository,
    private val requestRepository: com.shahaabapps.core.firebase.request.RequestRepository,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])
    private val today: String = LocalDate.now().toString()

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(AppSort.USED)
    private val filter = MutableStateFlow(AppFilter.ALL)
    /** Today's watched-app screenshots stored on this phone; refreshed as new ones arrive. */
    private val shotsToday = MutableStateFlow(0)

    init {
        countShotsToday()
        viewModelScope.launch { watchShotSync.run(childId) { countShotsToday() } }
    }

    private fun countShotsToday() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val start = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val uid = authRepository.currentUserIdOrNull() ?: return@launch
            shotsToday.value = com.shahaabapps.watchdog.feature.watch.LocalWatchStore
                .list(context, uid, childId).count { it.capturedAtMillis >= start && it.file != null }
        }
    }

    val uiState: StateFlow<UsageUiState> = combine(
        usageRepository.observeUsage(childId, today),
        appRulesRepository.observeInstalledApps(childId),
        combine(
            appRulesRepository.observeAppRules(childId),
            pendingAppsRepository.observe(childId),
            protectionRepository.observe(childId),
        ) { r, p, prot -> Triple(r, p, prot.blockNewApps && prot.newAppsBlocked) },
        combine(query, filter) { a, b -> a to b },
        combine(sort, shotsToday) { a, b -> a to b },
    ) { usage, apps, (rules, pending, newAppsBlocked), (q, selectedFilter), (selectedSort, todayShots) ->
        val rulesByPackage = rules.associateBy { it.packageName }
        val newApps = pending.map { it.packageName }.toSet()
        val total = apps.sumOf { usage[it.packageName] ?: 0 }
        val allRows = apps.map { app ->
            AppRow(
                app = app,
                minutes = usage[app.packageName] ?: 0,
                rule = rulesByPackage[app.packageName],
                newApp = app.packageName in newApps,
                newAppsBlocked = newAppsBlocked,
            )
        }
        var rows = when (selectedFilter) {
            AppFilter.ALL -> allRows
            AppFilter.RESTRICTED -> allRows.filter { it.restricted }
            AppFilter.BLOCKED -> allRows.filter { it.blocked }
            AppFilter.SUPERVISED -> allRows.filter { it.supervised }
        }
        if (q.isNotBlank()) {
            rows = rows.filter { it.app.label.contains(q, ignoreCase = true) }
        }
        rows = when (selectedSort) {
            AppSort.USED -> rows.sortedWith(compareByDescending<AppRow> { it.minutes }.thenBy { it.app.label.lowercase() })
            AppSort.NAME -> rows.sortedBy { it.app.label.lowercase() }
            AppSort.INSTALLED -> rows.sortedByDescending { it.app.installedAtMillis }
        }
        UsageUiState(
            loading = false,
            totalMinutes = total,
            query = q,
            sort = selectedSort,
            filter = selectedFilter,
            rows = rows,
            watchedApps = allRows.count { it.supervised },
            watchShotsToday = todayShots,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UsageUiState())

    /** The child's pending "remove the set hours" asks, by app package. */
    val hoursAsks: StateFlow<Map<String, com.shahaabapps.core.model.TimeRequest>> =
        requestRepository.observe(childId, 30)
            .map { list ->
                list.filter {
                    it.status == com.shahaabapps.core.model.RequestStatus.PENDING &&
                        it.kind == com.shahaabapps.core.model.RequestKind.LIFT_HOURS && it.appPackage.isNotBlank()
                }.associateBy { it.appPackage }
            }
            .catch { emit(emptyMap()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Approve the child's ask: the app keeps its rule but loses its hours. */
    fun liftHours(request: com.shahaabapps.core.model.TimeRequest) {
        viewModelScope.launch {
            val current = runCatching { appRulesRepository.observeAppRules(childId).first() }.getOrDefault(emptyList())
                .firstOrNull { it.packageName == request.appPackage }
            val saved = if (current != null) {
                appRulesRepository.saveAppRule(childId, current.copy(schedule = emptyList(), scheduleBlocks = false))
            } else {
                com.shahaabapps.core.common.AppResult.Success(Unit)
            }
            if (saved is com.shahaabapps.core.common.AppResult.Success) {
                requestRepository.respond(childId, request.id, true, 0L)
                Celebrations.saved()
            }
        }
    }

    /** Keep the hours: decline the child's ask. */
    fun declineHoursAsk(request: com.shahaabapps.core.model.TimeRequest) {
        viewModelScope.launch { requestRepository.respond(childId, request.id, false, 0L) }
    }

    fun setQuery(value: String) { query.value = value }
    fun setSort(value: AppSort) { sort.value = value }
    fun setFilter(value: AppFilter) { filter.value = value }

    fun saveRule(
        app: InstalledApp,
        dailyLimitMinutes: Int?,
        blocked: Boolean,
        schedule: List<TimeWindow>,
        watchEverySeconds: Int,
        /** [schedule] holds blocked hours, not allowed hours. */
        scheduleBlocks: Boolean = false,
        /** A new app awaiting review: the saved rule is the parent's decision. */
        clearReview: Boolean = false,
    ) {
        viewModelScope.launch {
            val saved = appRulesRepository.saveAppRule(
                childId,
                AppRule(
                    packageName = app.packageName,
                    appLabel = app.label,
                    dailyLimitMinutes = dailyLimitMinutes,
                    blocked = blocked,
                    schedule = schedule,
                    watchEverySeconds = watchEverySeconds,
                    scheduleBlocks = scheduleBlocks,
                ),
            )
            if (clearReview && saved is com.shahaabapps.core.common.AppResult.Success) {
                pendingAppsRepository.remove(childId, app.packageName)
            }
            // Hours removed by hand (or the app blocked/limited without hours):
            // a waiting "remove the hours" ask is answered yes.
            if (schedule.isEmpty() && saved is com.shahaabapps.core.common.AppResult.Success) {
                hoursAsks.value[app.packageName]?.let { requestRepository.respond(childId, it.id, true, 0L) }
            }
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
