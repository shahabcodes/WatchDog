package com.shahaabapps.shield.feature.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.focus.FocusRepository
import com.shahaabapps.core.firebase.geofence.GeofenceRepository
import com.shahaabapps.core.firebase.installguard.InstallGuardRepository
import com.shahaabapps.core.firebase.protection.ProtectionRepository
import com.shahaabapps.core.firebase.rules.AppRulesRepository
import com.shahaabapps.core.firebase.tracking.ChildTimeZoneRepository
import com.shahaabapps.core.firebase.tracking.TrackingRepository
import com.shahaabapps.core.model.AlertSettings
import com.shahaabapps.core.model.FocusSettings
import com.shahaabapps.core.model.Geofence
import com.shahaabapps.core.model.InstallGuard
import com.shahaabapps.core.model.ProtectionSettings
import com.shahaabapps.core.model.TrackingMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Everything the parent set for this phone, for Shield's read-only "Rules" screen.
 * [appLabels] names packages (focus allowlists, install-guard lists) by their label.
 */
data class RulesState(
    val focus: FocusSettings = FocusSettings(),
    val timeZone: String? = null,
    val protection: ProtectionSettings = ProtectionSettings(),
    val alerts: AlertSettings = AlertSettings(),
    val installGuard: InstallGuard = InstallGuard(),
    val places: List<Geofence> = emptyList(),
    val tracking: TrackingMode = TrackingMode.DEFAULT,
    val appLabels: Map<String, String> = emptyMap(),
    val appIcons: Map<String, String?> = emptyMap(),
)

private data class Part1(val focus: FocusSettings, val zone: String?, val protection: ProtectionSettings, val alerts: AlertSettings)

@HiltViewModel
class RulesViewModel @Inject constructor(
    authRepository: AuthRepository,
    focusRepository: FocusRepository,
    timeZoneRepository: ChildTimeZoneRepository,
    protectionRepository: ProtectionRepository,
    installGuardRepository: InstallGuardRepository,
    geofenceRepository: GeofenceRepository,
    trackingRepository: TrackingRepository,
    appRulesRepository: AppRulesRepository,
) : ViewModel() {

    private val childId: String? = authRepository.currentUserIdOrNull()

    /** A flow that starts with [initial] and survives errors (a missing doc keeps the default). */
    private fun <T> Flow<T>.safe(initial: T): Flow<T> = onStart { emit(initial) }.catch { emit(initial) }

    val state: StateFlow<RulesState> = (
        if (childId == null) {
            flowOf(RulesState())
        } else {
            val first = combine(
                focusRepository.observe(childId).safe(FocusSettings()),
                timeZoneRepository.observe(childId).safe(null),
                protectionRepository.observe(childId).safe(ProtectionSettings()),
                protectionRepository.observeAlerts(childId).safe(AlertSettings()),
            ) { focus, zone, protection, alerts -> Part1(focus, zone, protection, alerts) }
            combine(
                first,
                installGuardRepository.observe(childId).safe(InstallGuard()),
                geofenceRepository.observeGeofences(childId).safe(emptyList()),
                trackingRepository.observe(childId).safe(TrackingMode.DEFAULT),
                appRulesRepository.observeInstalledApps(childId).safe(emptyList()),
            ) { p, guard, places, tracking, apps ->
                RulesState(
                    focus = p.focus,
                    timeZone = p.zone,
                    protection = p.protection,
                    alerts = p.alerts,
                    installGuard = guard,
                    places = places.sortedBy { it.name.lowercase() },
                    tracking = tracking,
                    appLabels = apps.associate { it.packageName to it.label.ifBlank { it.packageName } },
                    appIcons = apps.associate { it.packageName to it.icon },
                )
            }
        }
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RulesState())
}
