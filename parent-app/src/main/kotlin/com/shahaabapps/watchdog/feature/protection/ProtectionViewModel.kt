package com.shahaabapps.watchdog.feature.protection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.protection.ProtectionRepository
import com.shahaabapps.core.model.ProtectionSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProtectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val protectionRepository: ProtectionRepository,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle["childId"])

    val settings: StateFlow<ProtectionSettings> = protectionRepository.observe(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProtectionSettings())

    fun save(settings: ProtectionSettings) {
        viewModelScope.launch {
            protectionRepository.save(childId, settings)
        }
    }
}
