package com.shahaabapps.watchdog.feature.overview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.child.ChildRepository
import com.shahaabapps.core.firebase.device.DeviceRepository
import com.shahaabapps.core.firebase.geofence.GeofenceRepository
import com.shahaabapps.core.firebase.rules.AppRulesRepository
import com.shahaabapps.core.firebase.usage.UsageRepository
import com.shahaabapps.core.model.ChildLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** One app's screen-time today, for the dashboard usage chart. */
data class AppUsageItem(
    val label: String,
    val iconBase64: String?,
    val minutes: Int,
)

/** One day's total screen time, for the weekly trend chart. */
data class DayUsage(
    val date: LocalDate,
    val minutes: Int,
    val isToday: Boolean,
)

data class OverviewUiState(
    val childName: String = "",
    val online: Boolean = false,
    val screenTimeMinutes: Int = 0,
    val limitedOrBlocked: Int = 0,
    val placesCount: Int = 0,
    val batteryPercent: Int = 0,
    val lastSeenMillis: Long = 0L,
    val deviceModel: String = "",
    val topApps: List<AppUsageItem> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class OverviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val deviceRepository: DeviceRepository,
    usageRepository: UsageRepository,
    appRulesRepository: AppRulesRepository,
    geofenceRepository: GeofenceRepository,
    childRepository: ChildRepository,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])
    private val todayDate: LocalDate = LocalDate.now()
    private val today: String = todayDate.toString()

    val childLocation: StateFlow<ChildLocation?> = deviceRepository.observeLocation(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Last 7 days of total screen time, oldest first, with zero-filled gaps. */
    val weekly: StateFlow<List<DayUsage>> =
        usageRepository.observeDailyTotals(childId, todayDate.minusDays(6).toString())
            .map { totals ->
                (0L..6L).map { offset ->
                    val date = todayDate.minusDays(6 - offset)
                    DayUsage(
                        date = date,
                        minutes = totals[date.toString()] ?: 0,
                        isToday = date == todayDate,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Re-evaluate online/offline on a tick (the child doc only changes every
    // heartbeat, so without this it would never flip back to offline).
    private val ticker = flow {
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(30_000)
        }
    }
    private val childWithTick = combine(childRepository.observeChild(childId), ticker) { child, _ -> child }

    val uiState: StateFlow<OverviewUiState> = combine(
        usageRepository.observeUsage(childId, today),
        appRulesRepository.observeInstalledApps(childId),
        appRulesRepository.observeAppRules(childId),
        geofenceRepository.observeGeofences(childId),
        childWithTick,
    ) { usage, apps, rules, geofences, child ->
        val knownPackages = apps.map { it.packageName }.toSet()
        val screenTime = usage.filterKeys { it in knownPackages }.values.sum()
        val lastSeen = child?.device?.lastSeenMillis ?: 0L
        val topApps = apps
            .mapNotNull { app ->
                val m = usage[app.packageName] ?: 0
                if (m > 0) AppUsageItem(app.label.ifBlank { app.packageName }, app.icon, m) else null
            }
            .sortedByDescending { it.minutes }
            .take(5)
        OverviewUiState(
            childName = child?.profile?.name ?: "Child",
            online = lastSeen > 0 && System.currentTimeMillis() - lastSeen < ONLINE_THRESHOLD_MS,
            screenTimeMinutes = screenTime,
            limitedOrBlocked = rules.count { it.blocked || it.dailyLimitMinutes != null },
            placesCount = geofences.size,
            batteryPercent = child?.device?.batteryPercent ?: 0,
            lastSeenMillis = lastSeen,
            deviceModel = child?.device?.model ?: "",
            topApps = topApps,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    private val _isRefreshing = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    /**
     * Manual pull-to-refresh: asks the child device to publish a fresh location fix
     * now; the dashboard's live listeners pick up the update.
     */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            runCatching { deviceRepository.requestLocation(childId) }
            kotlinx.coroutines.delay(1_200)
            _isRefreshing.value = false
        }
    }

    fun ringDevice(message: String) {
        viewModelScope.launch { deviceRepository.sendRing(childId, message) }
    }

    private companion object {
        const val ONLINE_THRESHOLD_MS = 5 * 60_000L
    }
}
