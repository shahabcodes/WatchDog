package com.shahaabapps.watchdog.feature.screenshot

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.screenshot.ScreenshotRepository
import com.shahaabapps.core.model.ScreenshotCommand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScreenshotViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val screenshotRepository: ScreenshotRepository,
) : ViewModel() {

    val childId: String = checkNotNull(savedStateHandle["childId"])

    val latest: StateFlow<ScreenshotCommand?> = screenshotRepository.observeLatest(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun requestScreenshot() {
        viewModelScope.launch { screenshotRepository.requestScreenshot(childId) }
    }
}
