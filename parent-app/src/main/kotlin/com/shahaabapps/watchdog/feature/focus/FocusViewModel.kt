package com.shahaabapps.watchdog.feature.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.focus.FocusRepository
import com.shahaabapps.core.model.FocusSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FocusViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val focusRepository: FocusRepository,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])

    val settings: StateFlow<FocusSettings> = focusRepository.observe(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusSettings())

    fun save(settings: FocusSettings) {
        viewModelScope.launch {
            focusRepository.save(childId, settings)
        }
    }
}
