package com.shahaabapps.watchdog.feature.travel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.device.DeviceRepository
import com.shahaabapps.core.firebase.geofence.GeofenceRepository
import com.shahaabapps.core.model.GeoMath
import com.shahaabapps.core.model.Geofence
import com.shahaabapps.core.model.TrailPoint
import com.shahaabapps.core.model.TravelStop
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

/** A travel stop plus the saved place (geofence) it falls inside, if any. */
data class TravelStopItem(
    val stop: TravelStop,
    val placeName: String?,
    val placeIcon: String? = null,
)

data class TravelHistoryUiState(
    val loading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val fromMinute: Int = 0,
    val toMinute: Int = 24 * 60 - 1,
    val stops: List<TravelStopItem> = emptyList(),
    val trail: List<TrailPoint> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TravelHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    deviceRepository: DeviceRepository,
    geofenceRepository: GeofenceRepository,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])
    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val fromMinute = MutableStateFlow(0)
    private val toMinute = MutableStateFlow(24 * 60 - 1)

    private val trailFlow = selectedDate.flatMapLatest { date ->
        deviceRepository.observeTrail(childId, date.toString())
    }

    private val filters = combine(selectedDate, fromMinute, toMinute) { d, f, t -> Triple(d, f, t) }

    val uiState: StateFlow<TravelHistoryUiState> = combine(
        trailFlow,
        deviceRepository.observeTravelHistory(childId),
        geofenceRepository.observeGeofences(childId),
        filters,
    ) { trail, stops, geofences, (date, from, to) ->
        val zone = ZoneId.systemDefault()
        val trailPts = trail.filter { p ->
            val z = Instant.ofEpochMilli(p.timestampMillis).atZone(zone)
            z.toLocalDate() == date && minuteOfDay(z) in from..to
        }
        val stopItems = stops
            .filter { s ->
                val z = Instant.ofEpochMilli(s.arrivalMillis).atZone(zone)
                z.toLocalDate() == date && minuteOfDay(z) in from..to
            }
            .map { s ->
                val place = placeFor(s.latitude, s.longitude, geofences)
                TravelStopItem(s, place?.name, place?.icon)
            }
        TravelHistoryUiState(
            loading = false,
            date = date,
            fromMinute = from,
            toMinute = to,
            stops = stopItems,
            trail = trailPts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TravelHistoryUiState())

    fun setDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun setTimeRange(from: Int, to: Int) {
        fromMinute.value = from.coerceIn(0, 24 * 60 - 1)
        toMinute.value = to.coerceIn(from, 24 * 60 - 1)
    }

    private fun minuteOfDay(z: ZonedDateTime): Int = z.hour * 60 + z.minute

    private fun placeFor(lat: Double, lon: Double, geofences: List<Geofence>): Geofence? =
        geofences
            .map { it to GeoMath.distanceMeters(lat, lon, it.latitude, it.longitude) }
            .filter { (g, d) -> d <= g.radiusMeters + 40 }
            .minByOrNull { it.second }
            ?.first
}
