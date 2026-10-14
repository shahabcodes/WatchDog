package com.shahaabapps.watchdog.feature.travel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.device.DeviceRepository
import com.shahaabapps.core.firebase.geofence.GeofenceRepository
import com.shahaabapps.core.model.GeoMath
import com.shahaabapps.core.model.Geofence
import com.shahaabapps.core.model.GeofenceShape
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
    /** True for the synthesized "where the child is right now" row. */
    val current: Boolean = false,
    /** Reverse-geocoded street/area label for stops not in a saved place. */
    val address: String? = null,
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
    private val addressResolver: AddressResolver,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])
    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val fromMinute = MutableStateFlow(0)
    private val toMinute = MutableStateFlow(24 * 60 - 1)

    private val trailFlow = selectedDate.flatMapLatest { date ->
        deviceRepository.observeTrail(childId, date.toString())
    }

    private val filters = combine(selectedDate, fromMinute, toMinute) { d, f, t -> Triple(d, f, t) }

    private val base: kotlinx.coroutines.flow.Flow<TravelHistoryUiState> = combine(
        trailFlow,
        deviceRepository.observeTravelHistory(childId),
        geofenceRepository.observeGeofences(childId),
        deviceRepository.observeLocation(childId),
        filters,
    ) { trail, stops, geofences, live, filter ->
        val (date, from, to) = filter
        val zone = ZoneId.systemDefault()
        val trailPts = trail.filter { p ->
            val z = Instant.ofEpochMilli(p.timestampMillis).atZone(zone)
            z.toLocalDate() == date && minuteOfDay(z) in from..to
        }
        val recorded = stops
            .filter { s ->
                val z = Instant.ofEpochMilli(s.arrivalMillis).atZone(zone)
                z.toLocalDate() == date && minuteOfDay(z) in from..to
            }
            .map { s ->
                val place = placeFor(s.latitude, s.longitude, geofences)
                TravelStopItem(s, place?.name, place?.icon)
            }
        // When viewing today, prepend where the child is right now (so the
        // current place shows before they leave it), unless it's already the
        // most recent recorded stop.
        val current = if (date == LocalDate.now() && live != null) {
            val place = placeFor(live.latitude, live.longitude, geofences)
            val latest = recorded.firstOrNull()
            val sameAsLatest = latest != null &&
                GeoMath.distanceMeters(latest.stop.latitude, latest.stop.longitude, live.latitude, live.longitude) <= 120
            if (sameAsLatest) {
                null
            } else {
                TravelStopItem(
                    stop = TravelStop(
                        id = "current",
                        latitude = live.latitude,
                        longitude = live.longitude,
                        arrivalMillis = live.timestampMillis,
                        departureMillis = live.timestampMillis,
                    ),
                    placeName = place?.name,
                    placeIcon = place?.icon,
                    current = true,
                )
            }
        } else {
            null
        }
        val stopItems = listOfNotNull(current) + recorded
        TravelHistoryUiState(
            loading = false,
            date = date,
            fromMinute = from,
            toMinute = to,
            stops = stopItems,
            trail = trailPts,
        )
    }

    // Layer reverse-geocoded street/area labels onto stops that aren't in a saved
    // place, filling in as they resolve (and triggering lookups on demand).
    val uiState: StateFlow<TravelHistoryUiState> = combine(base, addressResolver.addresses) { state, addrs ->
        val stops = state.stops.map { item ->
            if (!item.current && item.placeName == null) {
                addressResolver.request(item.stop.latitude, item.stop.longitude)
                item.copy(address = addrs[addressResolver.keyOf(item.stop.latitude, item.stop.longitude)])
            } else {
                item
            }
        }
        state.copy(stops = stops)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TravelHistoryUiState())

    fun setDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun setTimeRange(from: Int, to: Int) {
        fromMinute.value = from.coerceIn(0, 24 * 60 - 1)
        toMinute.value = to.coerceIn(from, 24 * 60 - 1)
    }

    private fun minuteOfDay(z: ZonedDateTime): Int = z.hour * 60 + z.minute

    private fun placeFor(lat: Double, lon: Double, geofences: List<Geofence>): Geofence? {
        // A drawn area the stop falls inside wins outright (shape-accurate).
        geofences.firstOrNull { g ->
            g.shape == GeofenceShape.POLYGON && g.points.size >= 3 &&
                GeoMath.containsPoint(g.points, lat, lon)
        }?.let { return it }
        // Otherwise the nearest place whose centre is within its radius plus a
        // GPS-forgiving margin (the recorded stop point carries drift and sits at
        // the dwell-cluster anchor, not the exact centre).
        return geofences
            .map { it to GeoMath.distanceMeters(lat, lon, it.latitude, it.longitude) }
            .filter { (g, d) -> d <= g.radiusMeters + MATCH_MARGIN_METERS }
            .minByOrNull { it.second }
            ?.first
    }

    private companion object {
        /** Extra slack beyond a place's radius when labelling a stop (metres). */
        const val MATCH_MARGIN_METERS = 80.0
    }
}
