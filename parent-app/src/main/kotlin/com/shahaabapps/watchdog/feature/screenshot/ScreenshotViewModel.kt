package com.shahaabapps.watchdog.feature.screenshot

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.screenshot.ScreenshotRepository
import com.shahaabapps.core.model.Screenshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScreenshotViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ScreenshotRepository,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])

    val screenshots: StateFlow<List<Screenshot>> = repository.observeScreenshots(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun request() {
        viewModelScope.launch { repository.requestCapture(childId) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(childId, id) }
    }
}
