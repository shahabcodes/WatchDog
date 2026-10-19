package com.shahaabapps.watchdog.feature.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.focus.FocusRepository
import com.shahaabapps.core.firebase.rules.AppRulesRepository
import com.shahaabapps.core.model.FocusSettings
import com.shahaabapps.core.model.InstalledApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FocusViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val focusRepository: FocusRepository,
    appRulesRepository: AppRulesRepository,
    private val timeZoneRepository: com.shahaabapps.core.firebase.tracking.ChildTimeZoneRepository,
    childRepository: com.shahaabapps.core.firebase.child.ChildRepository,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])

    val settings: StateFlow<FocusSettings> = focusRepository.observe(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusSettings())

    /** Installed apps on the child device, for the per-window allowlist picker. */
    val installedApps: StateFlow<List<InstalledApp>> =
        appRulesRepository.observeInstalledApps(childId)
            .map { apps -> apps.sortedBy { it.label.lowercase() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The zone schedules follow (the parent's choice, else what Shield reports it
     * uses) and the zone the phone is set to. Either may be "" until known.
     */
    val zones: StateFlow<ChildZones> = kotlinx.coroutines.flow.combine(
        timeZoneRepository.observe(childId),
        childRepository.observeChild(childId),
    ) { parentZone, child ->
        val guard = child?.device?.guard
        ChildZones(
            home = parentZone ?: guard?.homeZone.orEmpty(),
            phone = guard?.phoneZone.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChildZones())

    fun setZone(zoneId: String) {
        viewModelScope.launch { timeZoneRepository.setZone(childId, zoneId) }
    }

    fun save(settings: FocusSettings) {
        viewModelScope.launch {
            focusRepository.save(childId, settings)
        }
    }
}

/** The child's home time zone (schedules) and the phone's current zone, as IANA ids. */
data class ChildZones(val home: String = "", val phone: String = "") {
    /** True when the phone is on a different UTC offset than home right now. */
    val away: Boolean
        get() = home.isNotBlank() && phone.isNotBlank() &&
            zoneOffset(home) != null && zoneOffset(home) != zoneOffset(phone)
}

internal fun zoneOffset(id: String): java.time.ZoneOffset? =
    runCatching { java.time.ZoneId.of(id).rules.getOffset(java.time.Instant.now()) }.getOrNull()

/** "Asia/Dubai" → "Dubai". */
fun zoneCity(id: String): String = id.substringAfterLast('/').replace('_', ' ')
