package com.shahaabapps.watchdog.feature.alerts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.geofence.GeofenceRepository
import com.shahaabapps.core.model.GeofenceEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One alert row: the raw event plus the resolved place name and whether that
 * place is a currently-saved location (so unknown places read differently).
 */
data class AlertItem(
    val event: GeofenceEvent,
    val placeName: String?,
    val known: Boolean,
)

/** Feed of place arrival/departure alerts for the current child. */
@HiltViewModel
class AlertsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val geofenceRepository: GeofenceRepository,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])

    // Join events with the current geofences so each alert shows the saved
    // place's name, and so an event whose place no longer exists (or never had
    // one) is marked unknown.
    val items: StateFlow<List<AlertItem>> = combine(
        geofenceRepository.observeRecentEvents(childId, 50L),
        geofenceRepository.observeGeofences(childId),
    ) { events, geofences ->
        val byId = geofences.associateBy { it.id }
        events.map { e ->
            val saved = byId[e.geofenceId]
            val name = saved?.name?.ifBlank { null } ?: e.geofenceName.ifBlank { null }
            AlertItem(event = e, placeName = name, known = saved != null || e.geofenceName.isNotBlank())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Clears the whole notifications feed for this child. */
    fun clearAll() {
        viewModelScope.launch { geofenceRepository.clearEvents(childId) }
    }
}
