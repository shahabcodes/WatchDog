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
import com.shahaabapps.core.model.GeofenceTransition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class OverviewUiState(
    val childName: String = "",
    val online: Boolean = false,
    val screenTimeMinutes: Int = 0,
    val limitedOrBlocked: Int = 0,
    val placesCount: Int = 0,
    val batteryPercent: Int = 0,
    val lastSeenMillis: Long = 0L,
    val deviceModel: String = "",
    val loading: Boolean = true,
)

/** One recent geofence enter/exit, for the dashboard activity feed. */
data class ActivityItem(
    val placeName: String,
    val entered: Boolean,
    val timeMillis: Long,
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
    private val today: String = LocalDate.now().toString()

    val childLocation: StateFlow<ChildLocation?> = deviceRepository.observeLocation(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
        OverviewUiState(
            childName = child?.profile?.name ?: "Child",
            online = lastSeen > 0 && System.currentTimeMillis() - lastSeen < ONLINE_THRESHOLD_MS,
            screenTimeMinutes = screenTime,
            limitedOrBlocked = rules.count { it.blocked || it.dailyLimitMinutes != null },
            placesCount = geofences.size,
            batteryPercent = child?.device?.batteryPercent ?: 0,
            lastSeenMillis = lastSeen,
            deviceModel = child?.device?.model ?: "",
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    val recentActivity: StateFlow<List<ActivityItem>> = combine(
        geofenceRepository.observeRecentEvents(childId, 6),
        geofenceRepository.observeGeofences(childId),
    ) { events, geofences ->
        val names = geofences.associate { it.id to it.name }
        events.map { e ->
            ActivityItem(
                placeName = names[e.geofenceId] ?: e.geofenceName.ifBlank { "a place" },
                entered = e.transition == GeofenceTransition.ENTER,
                timeMillis = e.timestampMillis,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun ringDevice(message: String) {
        viewModelScope.launch { deviceRepository.sendRing(childId, message) }
    }

    private companion object {
        const val ONLINE_THRESHOLD_MS = 5 * 60_000L
    }
}
