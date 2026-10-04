package com.shahaabapps.watchdog.feature.travel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.device.DeviceRepository
import com.shahaabapps.core.firebase.geofence.GeofenceRepository
import com.shahaabapps.core.model.GeoMath
import com.shahaabapps.core.model.Geofence
import com.shahaabapps.core.model.TravelStop
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class TravelRange(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    WEEK("This week"),
    ALL("All"),
}

/** Time-of-day buckets (by arrival hour). */
enum class TravelTime(val label: String, val startHour: Int, val endHour: Int) {
    ANY("All day", 0, 24),
    MORNING("Morning", 5, 12),
    AFTERNOON("Afternoon", 12, 17),
    EVENING("Evening", 17, 21),
    NIGHT("Night", 21, 29); // 21:00–04:59 (wraps past midnight)

    fun contains(hour: Int): Boolean {
        if (this == ANY) return true
        return if (endHour <= 24) hour in startHour until endHour
        else hour >= startHour || hour < (endHour - 24)
    }
}

/** A travel stop plus the saved place (geofence) it falls inside, if any. */
data class TravelStopItem(
    val stop: TravelStop,
    val placeName: String?,
    val placeIcon: String? = null,
)

data class TravelHistoryUiState(
    val loading: Boolean = true,
    val range: TravelRange = TravelRange.TODAY,
    val time: TravelTime = TravelTime.ANY,
    val stops: List<TravelStopItem> = emptyList(),
)

@HiltViewModel
class TravelHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    deviceRepository: DeviceRepository,
    geofenceRepository: GeofenceRepository,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])
    private val range = MutableStateFlow(TravelRange.TODAY)
    private val time = MutableStateFlow(TravelTime.ANY)

    val uiState: StateFlow<TravelHistoryUiState> = combine(
        deviceRepository.observeTravelHistory(childId),
        geofenceRepository.observeGeofences(childId),
        range,
        time,
    ) { stops, geofences, selectedRange, selectedTime ->
        val zone = ZoneId.systemDefault()
        val items = stops
            .filter { stop ->
                inRange(stop.arrivalMillis, selectedRange) &&
                    selectedTime.contains(Instant.ofEpochMilli(stop.arrivalMillis).atZone(zone).hour)
            }
            .map { stop ->
                val place = placeFor(stop.latitude, stop.longitude, geofences)
                TravelStopItem(stop, place?.name, place?.icon)
            }
        TravelHistoryUiState(
            loading = false,
            range = selectedRange,
            time = selectedTime,
            stops = items,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TravelHistoryUiState())

    /** The saved place this point sits inside (nearest within its radius), or null. */
    private fun placeFor(lat: Double, lon: Double, geofences: List<Geofence>): Geofence? =
        geofences
            .map { it to GeoMath.distanceMeters(lat, lon, it.latitude, it.longitude) }
            .filter { (g, d) -> d <= g.radiusMeters + 40 }
            .minByOrNull { it.second }
            ?.first

    fun setRange(newRange: TravelRange) {
        range.value = newRange
    }

    fun setTime(newTime: TravelTime) {
        time.value = newTime
    }

    private fun inRange(millis: Long, range: TravelRange): Boolean {
        if (range == TravelRange.ALL) return true
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when (range) {
            TravelRange.TODAY -> day == today
            TravelRange.YESTERDAY -> day == today.minusDays(1)
            TravelRange.WEEK -> !day.isBefore(today.minusDays(6)) && !day.isAfter(today)
            TravelRange.ALL -> true
        }
    }
}
