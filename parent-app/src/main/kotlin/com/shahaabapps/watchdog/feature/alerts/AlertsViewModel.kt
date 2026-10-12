package com.shahaabapps.watchdog.feature.alerts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.geofence.GeofenceRepository
import com.shahaabapps.core.model.GeofenceEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Feed of place arrival/departure alerts for the current child. */
@HiltViewModel
class AlertsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val geofenceRepository: GeofenceRepository,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])

    val events: StateFlow<List<GeofenceEvent>> =
        geofenceRepository.observeRecentEvents(childId, 50L)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Clears the whole notifications feed for this child. */
    fun clearAll() {
        viewModelScope.launch { geofenceRepository.clearEvents(childId) }
    }
}
