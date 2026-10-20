package com.shahaabapps.shield.enforcement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.protection.ProtectionRepository
import com.shahaabapps.core.model.ProtectionSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ProtectionStatusViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val protectionRepository: ProtectionRepository,
) : ViewModel() {

    private val childId: String? = authRepository.currentUserIdOrNull()

    val settings: StateFlow<ProtectionSettings> =
        if (childId != null) {
            protectionRepository.observe(childId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProtectionSettings())
        } else {
            MutableStateFlow(ProtectionSettings())
        }

    /** Checks a typed removal PIN on the server; null = OK, else an error message. */
    suspend fun verifyPin(pin: String): String? =
        when (val r = protectionRepository.verifyRemovalPin(pin)) {
            is com.shahaabapps.core.common.AppResult.Success -> null
            // Offline errors already read "No internet connection…" (callSafely).
            is com.shahaabapps.core.common.AppResult.Failure -> r.error.message ?: "Incorrect PIN"
        }
}
