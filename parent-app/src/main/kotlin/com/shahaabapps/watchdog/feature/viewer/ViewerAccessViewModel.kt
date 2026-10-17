package com.shahaabapps.watchdog.feature.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.viewer.ViewerAccessRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ViewerAccessViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ViewerAccessRepository,
) : ViewModel() {
    private val childId: String = checkNotNull(savedStateHandle["childId"])

    val enabled: StateFlow<Boolean> = repository.observe(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(childId, enabled) }
    }
}
