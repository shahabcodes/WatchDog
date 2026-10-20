package com.shahaabapps.watchdog.feature.protection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.protection.ProtectionRepository
import com.shahaabapps.core.model.ProtectionSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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

    init {
        // Move an old PIN hash out of the child-readable doc to the server-only
        // store, once, so the parent doesn't have to re-enter the PIN.
        viewModelScope.launch {
            protectionRepository.observe(childId)
                .first { it.needsPinMigration }
            protectionRepository.migratePin(childId)
        }
    }

    /** Which alerts parents get for this child. */
    val alerts: StateFlow<com.shahaabapps.core.model.AlertSettings> = protectionRepository.observeAlerts(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.shahaabapps.core.model.AlertSettings())

    /** [newPin] is the plaintext PIN to (re)set, or null to keep the existing one. */
    fun save(
        uninstallBlocked: Boolean,
        blockNewApps: Boolean,
        lockSettings: Boolean,
        newPin: String?,
        alerts: com.shahaabapps.core.model.AlertSettings,
    ) {
        viewModelScope.launch {
            protectionRepository.save(childId, uninstallBlocked, blockNewApps, lockSettings, newPin)
            if (alerts != this@ProtectionViewModel.alerts.value) protectionRepository.saveAlerts(childId, alerts)
        }
    }
}
