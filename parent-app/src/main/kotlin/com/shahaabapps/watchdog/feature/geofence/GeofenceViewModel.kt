package com.shahaabapps.watchdog.feature.geofence

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.geofence.GeofenceRepository
import com.shahaabapps.core.model.GeoMath
import com.shahaabapps.core.model.GeoPoint
import com.shahaabapps.core.model.Geofence
import com.shahaabapps.core.model.GeofenceEvent
import com.shahaabapps.core.model.GeofenceShape
import com.shahaabapps.core.model.GeofenceTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GeofenceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val geofenceRepository: GeofenceRepository,
) : ViewModel() {

    val childId: String = checkNotNull(savedStateHandle["childId"])

    val geofences: StateFlow<List<Geofence>> = geofenceRepository.observeGeofences(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val events: StateFlow<List<GeofenceEvent>> = geofenceRepository.observeRecentEvents(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveGeofence(
        name: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
        trigger: GeofenceTrigger,
    ) {
        viewModelScope.launch {
            geofenceRepository.saveGeofence(
                childId,
                Geofence(
                    name = name.ifBlank { "Place" },
                    latitude = latitude,
                    longitude = longitude,
                    radiusMeters = radiusMeters,
                    trigger = trigger,
                    active = true,
                ),
            )
        }
    }

    /** Saves a free-form drawn area; its bounding circle is used for triggering. */
    fun saveArea(
        name: String,
        points: List<GeoPoint>,
        trigger: GeofenceTrigger,
    ) {
        if (points.size < 3) return
        val (centerLat, centerLon, radius) = GeoMath.boundingCircle(points)
        viewModelScope.launch {
            geofenceRepository.saveGeofence(
                childId,
                Geofence(
                    name = name.ifBlank { "Area" },
                    latitude = centerLat,
                    longitude = centerLon,
                    radiusMeters = radius,
                    trigger = trigger,
                    active = true,
                    shape = GeofenceShape.POLYGON,
                    points = points,
                ),
            )
        }
    }

    fun deleteGeofence(geofenceId: String) {
        viewModelScope.launch { geofenceRepository.deleteGeofence(childId, geofenceId) }
    }
}

