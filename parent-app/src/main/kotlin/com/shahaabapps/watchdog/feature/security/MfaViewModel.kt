package com.shahaabapps.watchdog.feature.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.auth.TotpEnrollment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which screen of the 2FA flow is showing. */
enum class MfaStep { OVERVIEW, REAUTH, ENROLL }

/** What the password re-auth will lead into. */
enum class MfaIntent { ENABLE, DISABLE }

data class MfaUiState(
    val enrolled: Boolean = false,
    val step: MfaStep = MfaStep.OVERVIEW,
    val intent: MfaIntent? = null,
    val password: String = "",
    val enrollment: TotpEnrollment? = null,
    val code: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

@HiltViewModel
class MfaViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MfaUiState(enrolled = authRepository.isMfaEnrolled()))
    val uiState: StateFlow<MfaUiState> = _uiState.asStateFlow()

    fun startEnable() = _uiState.update { it.copy(step = MfaStep.REAUTH, intent = MfaIntent.ENABLE, password = "", error = null, info = null) }
    fun startDisable() = _uiState.update { it.copy(step = MfaStep.REAUTH, intent = MfaIntent.DISABLE, password = "", error = null, info = null) }
    fun cancel() = _uiState.update { it.copy(step = MfaStep.OVERVIEW, password = "", code = "", enrollment = null, error = null) }

    fun onPassword(value: String) = _uiState.update { it.copy(password = value, error = null) }
    fun onCode(value: String) = _uiState.update { it.copy(code = value.filter { c -> c.isDigit() }.take(6), error = null) }

    /** Re-authenticate with the password, then begin enrollment or disable MFA. */
    fun submitReauth() {
        val s = _uiState.value
        if (s.password.isEmpty() || s.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = authRepository.reauthenticate(s.password)) {
                is AppResult.Failure -> _uiState.update { it.copy(busy = false, error = r.error.message ?: "Incorrect password") }
                is AppResult.Success -> if (s.intent == MfaIntent.ENABLE) beginEnroll() else disable()
            }
        }
    }

    private suspend fun beginEnroll() {
        when (val r = authRepository.beginTotpEnrollment()) {
            is AppResult.Success -> _uiState.update {
                it.copy(busy = false, step = MfaStep.ENROLL, enrollment = r.data, password = "", code = "")
            }
            is AppResult.Failure -> _uiState.update { it.copy(busy = false, error = r.error.message ?: "Couldn't start setup") }
        }
    }

    private suspend fun disable() {
        when (val r = authRepository.disableMfa()) {
            is AppResult.Success -> _uiState.update {
                it.copy(busy = false, enrolled = false, step = MfaStep.OVERVIEW, password = "", info = "Two-factor authentication turned off.")
            }
            is AppResult.Failure -> _uiState.update { it.copy(busy = false, error = r.error.message ?: "Couldn't turn off 2FA") }
        }
    }

    /** Confirm enrollment with the 6-digit code from the authenticator app. */
    fun confirmEnroll() {
        val s = _uiState.value
        if (s.code.length != 6 || s.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = authRepository.confirmTotpEnrollment(s.code)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(busy = false, enrolled = true, step = MfaStep.OVERVIEW, enrollment = null, code = "", info = "Two-factor authentication is on.")
                }
                is AppResult.Failure -> _uiState.update { it.copy(busy = false, error = r.error.message ?: "Incorrect code — try again") }
            }
        }
    }

    fun clearInfo() = _uiState.update { it.copy(info = null) }
}
