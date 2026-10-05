package com.shahaabapps.watchdog.feature.geofence

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.device.DeviceRepository
import com.shahaabapps.core.firebase.geofence.GeofenceRepository
import com.shahaabapps.core.model.ChildLocation
import com.shahaabapps.core.model.GeoMath
import com.shahaabapps.core.model.GeoPoint
import com.shahaabapps.core.model.Geofence
import com.shahaabapps.core.model.GeofenceEvent
import com.shahaabapps.core.model.GeofenceShape
import com.shahaabapps.core.model.GeofenceTrigger
import com.shahaabapps.core.ui.components.Celebrations
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
    private val deviceRepository: DeviceRepository,
) : ViewModel() {

    val childId: String = checkNotNull(savedStateHandle["childId"])

    val geofences: StateFlow<List<Geofence>> = geofenceRepository.observeGeofences(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val events: StateFlow<List<GeofenceEvent>> = geofenceRepository.observeRecentEvents(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val childLocation: StateFlow<ChildLocation?> = deviceRepository.observeLocation(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun ringDevice(message: String) {
        viewModelScope.launch { deviceRepository.sendRing(childId, message) }
    }

    fun saveGeofence(
        name: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
        trigger: GeofenceTrigger,
        icon: String = "place",
        active: Boolean = true,
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
                    active = active,
                    icon = icon,
                ),
            )
            Celebrations.saved()
        }
    }

    /** Saves a free-form drawn area; its bounding circle is used for triggering. */
    fun saveArea(
        name: String,
        points: List<GeoPoint>,
        trigger: GeofenceTrigger,
        icon: String = "place",
        active: Boolean = true,
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
                    active = active,
                    shape = GeofenceShape.POLYGON,
                    points = points,
                    icon = icon,
                ),
            )
            Celebrations.saved()
        }
    }

    /** Updates an existing geofence's name, icon, alerts (and radius for circles). */
    fun updateGeofence(
        original: Geofence,
        name: String,
        radiusMeters: Float,
        icon: String,
        trigger: GeofenceTrigger,
        active: Boolean,
    ) {
        viewModelScope.launch {
            geofenceRepository.saveGeofence(
                childId,
                original.copy(
                    name = name.ifBlank { original.name },
                    radiusMeters = radiusMeters,
                    icon = icon,
                    trigger = trigger,
                    active = active,
                ),
            )
            Celebrations.saved()
        }
    }

    fun deleteGeofence(geofenceId: String) {
        viewModelScope.launch {
            geofenceRepository.deleteGeofence(childId, geofenceId)
            Celebrations.deleted()
        }
    }
}

