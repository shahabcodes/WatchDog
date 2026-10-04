package com.shahaabapps.watchdog.feature.travel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.device.DeviceRepository
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

data class TravelHistoryUiState(
    val loading: Boolean = true,
    val range: TravelRange = TravelRange.TODAY,
    val stops: List<TravelStop> = emptyList(),
)

@HiltViewModel
class TravelHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    deviceRepository: DeviceRepository,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])
    private val range = MutableStateFlow(TravelRange.TODAY)

    val uiState: StateFlow<TravelHistoryUiState> = combine(
        deviceRepository.observeTravelHistory(childId),
        range,
    ) { stops, selectedRange ->
        TravelHistoryUiState(
            loading = false,
            range = selectedRange,
            stops = stops.filter { inRange(it.arrivalMillis, selectedRange) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TravelHistoryUiState())

    fun setRange(newRange: TravelRange) {
        range.value = newRange
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
