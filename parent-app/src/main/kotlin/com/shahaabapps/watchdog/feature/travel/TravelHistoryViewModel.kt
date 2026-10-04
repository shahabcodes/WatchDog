package com.shahaabapps.watchdog.feature.travel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.device.DeviceRepository
import com.shahaabapps.core.model.TravelStop
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TravelHistoryUiState(
    val loading: Boolean = true,
    val stops: List<TravelStop> = emptyList(),
)

@HiltViewModel
class TravelHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    deviceRepository: DeviceRepository,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])

    val uiState: StateFlow<TravelHistoryUiState> =
        deviceRepository.observeTravelHistory(childId)
            .map { TravelHistoryUiState(loading = false, stops = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TravelHistoryUiState())
}
