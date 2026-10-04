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
    private val today: String = LocalDate.now().toString()

    val childLocation: StateFlow<ChildLocation?> = deviceRepository.observeLocation(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<OverviewUiState> = combine(
        usageRepository.observeUsage(childId, today),
        appRulesRepository.observeInstalledApps(childId),
        appRulesRepository.observeAppRules(childId),
        geofenceRepository.observeGeofences(childId),
        childRepository.observeChild(childId),
    ) { usage, apps, rules, geofences, child ->
        val knownPackages = apps.map { it.packageName }.toSet()
        val screenTime = usage.filterKeys { it in knownPackages }.values.sum()
        OverviewUiState(
            childName = child?.profile?.name ?: "Child",
            online = child?.device?.online ?: false,
            screenTimeMinutes = screenTime,
            limitedOrBlocked = rules.count { it.blocked || it.dailyLimitMinutes != null },
            placesCount = geofences.size,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    fun ringDevice(message: String) {
        viewModelScope.launch { deviceRepository.sendRing(childId, message) }
    }
}
