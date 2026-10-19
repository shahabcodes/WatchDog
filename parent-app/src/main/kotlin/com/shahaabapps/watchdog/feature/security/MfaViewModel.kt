package com.shahaabapps.watchdog.feature.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.auth.BackupCodeStatus
import com.shahaabapps.core.firebase.auth.SignInOutcome
import com.shahaabapps.core.firebase.auth.TotpEnrollment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which screen of the 2FA flow is showing. */
enum class MfaStep { OVERVIEW, VERIFY_EMAIL, REAUTH, REAUTH_CODE, ENROLL, CODES }

/** What the password re-auth will lead into. */
enum class MfaIntent { ENABLE, DISABLE, REGENERATE }

data class MfaUiState(
    val enrolled: Boolean = false,
    val step: MfaStep = MfaStep.OVERVIEW,
    val intent: MfaIntent? = null,
    val password: String = "",
    val enrollment: TotpEnrollment? = null,
    val code: String = "",
    /** On the re-auth code step: typing a backup code instead of an authenticator code. */
    val useBackupCode: Boolean = false,
    val backupCode: String = "",
    /** Freshly issued backup codes, shown once on the CODES step. */
    val backupCodes: List<String> = emptyList(),
    /** Unused backup codes left; null while unknown (loading / failed / 2FA off). */
    val codeStatus: BackupCodeStatus? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val email: String = "",
    val verificationSent: Boolean = false,
)

@HiltViewModel
class MfaViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MfaUiState(enrolled = authRepository.isMfaEnrolled()))
    val uiState: StateFlow<MfaUiState> = _uiState.asStateFlow()

    init {
        refreshCodeStatus()
    }

    private fun refreshCodeStatus() {
        if (!_uiState.value.enrolled) {
            _uiState.update { it.copy(codeStatus = null) }
            return
        }
        viewModelScope.launch {
            val status = (authRepository.backupCodeStatus() as? AppResult.Success)?.data
            _uiState.update { it.copy(codeStatus = status) }
        }
    }

    fun startEnable() {
        // Firebase requires a verified email before enrolling a second factor.
        if (!authRepository.isEmailVerified()) {
            _uiState.update {
                it.copy(
                    step = MfaStep.VERIFY_EMAIL,
                    intent = MfaIntent.ENABLE,
                    email = authRepository.currentUserEmail() ?: "",
                    error = null, info = null, verificationSent = false,
                )
            }
        } else {
            startReauth(MfaIntent.ENABLE)
        }
    }
    fun startDisable() = startReauth(MfaIntent.DISABLE)
    fun startRegenerate() = startReauth(MfaIntent.REGENERATE)

    private fun startReauth(intent: MfaIntent) = _uiState.update {
        it.copy(step = MfaStep.REAUTH, intent = intent, password = "", code = "", backupCode = "", useBackupCode = false, error = null, info = null)
    }

    fun cancel() = _uiState.update {
        it.copy(step = MfaStep.OVERVIEW, password = "", code = "", backupCode = "", useBackupCode = false, enrollment = null, error = null)
    }

    /** Send the verification link to the account's email. */
    fun sendVerificationEmail() {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = authRepository.sendEmailVerification()) {
                is AppResult.Success -> _uiState.update {
                    it.copy(busy = false, verificationSent = true, info = "Verification link sent to ${it.email}. Tap it, then press 'I've verified'.")
                }
                is AppResult.Failure -> _uiState.update { it.copy(busy = false, error = r.error.message ?: "Couldn't send the email") }
            }
        }
    }

    /** After the user taps the email link: reload and continue if now verified. */
    fun checkedVerification() {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            authRepository.reloadUser()
            if (authRepository.isEmailVerified()) {
                _uiState.update { it.copy(busy = false, step = MfaStep.REAUTH, password = "", info = null) }
            } else {
                _uiState.update { it.copy(busy = false, error = "Still not verified. Open the link in your email, then try again.") }
            }
        }
    }

    fun onPassword(value: String) = _uiState.update { it.copy(password = value, error = null) }
    fun onCode(value: String) = _uiState.update { it.copy(code = value.filter { c -> c.isDigit() }.take(6), error = null) }
    fun onBackupCode(value: String) = _uiState.update { it.copy(backupCode = value.take(20), error = null) }
    fun toggleBackupCode() = _uiState.update { it.copy(useBackupCode = !it.useBackupCode, code = "", backupCode = "", error = null) }

    /** Re-authenticate with the password; with 2FA on, this continues to the code step. */
    fun submitReauth() {
        val s = _uiState.value
        if (s.password.isEmpty() || s.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = authRepository.reauthenticate(s.password)) {
                is AppResult.Failure -> _uiState.update { it.copy(busy = false, error = r.error.message ?: "Incorrect password") }
                is AppResult.Success -> when (r.data) {
                    is SignInOutcome.Success -> proceed()
                    SignInOutcome.MfaRequired -> _uiState.update {
                        it.copy(busy = false, step = MfaStep.REAUTH_CODE, code = "", backupCode = "", useBackupCode = false)
                    }
                }
            }
        }
    }

    /** Finish the re-auth with the authenticator code, or turn 2FA off with a backup code. */
    fun submitReauthCode() {
        val s = _uiState.value
        if (s.busy) return
        if (s.useBackupCode) {
            if (s.backupCode.isBlank()) return
            _uiState.update { it.copy(busy = true, error = null) }
            viewModelScope.launch {
                when (val r = authRepository.disableMfaWithBackupCode(s.password, s.backupCode)) {
                    is AppResult.Success -> _uiState.update {
                        it.copy(
                            busy = false, enrolled = false, step = MfaStep.OVERVIEW, password = "", backupCode = "",
                            useBackupCode = false, codeStatus = null,
                            info = "Backup code accepted. Two-factor is off — turn it on again with your new authenticator.",
                        )
                    }
                    is AppResult.Failure -> _uiState.update { it.copy(busy = false, error = r.error.message ?: "Wrong backup code") }
                }
            }
        } else {
            if (s.code.length != 6) return
            _uiState.update { it.copy(busy = true, error = null) }
            viewModelScope.launch {
                when (val r = authRepository.resolveMfa(s.code)) {
                    is AppResult.Success -> proceed()
                    is AppResult.Failure -> _uiState.update { it.copy(busy = false, error = r.error.message ?: "Incorrect code") }
                }
            }
        }
    }

    /** Re-auth done: carry out what it was for. */
    private suspend fun proceed() {
        when (_uiState.value.intent) {
            MfaIntent.ENABLE -> beginEnroll()
            MfaIntent.DISABLE -> disable()
            MfaIntent.REGENERATE, null -> issueCodes(afterEnroll = false)
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
                it.copy(busy = false, enrolled = false, step = MfaStep.OVERVIEW, password = "", code = "", codeStatus = null, info = "Two-factor authentication turned off.")
            }
            is AppResult.Failure -> _uiState.update { it.copy(busy = false, error = r.error.message ?: "Couldn't turn off 2FA") }
        }
    }

    /** Ask the server for 10 new backup codes and show them once. */
    private suspend fun issueCodes(afterEnroll: Boolean) {
        when (val r = authRepository.generateBackupCodes()) {
            is AppResult.Success -> _uiState.update {
                it.copy(busy = false, step = MfaStep.CODES, backupCodes = r.data, password = "", code = "", info = null, error = null)
            }
            is AppResult.Failure -> _uiState.update {
                it.copy(
                    busy = false, step = MfaStep.OVERVIEW, password = "", code = "",
                    info = if (afterEnroll) "Two-factor authentication is on." else null,
                    error = "Couldn't create backup codes (${r.error.message ?: "network error"}). Tap \"Get new backup codes\" to try again.",
                )
            }
        }
    }

    /** Confirm enrollment with the 6-digit code, then issue backup codes. */
    fun confirmEnroll() {
        val s = _uiState.value
        if (s.code.length != 6 || s.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = authRepository.confirmTotpEnrollment(s.code)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(enrolled = true, enrollment = null) }
                    issueCodes(afterEnroll = true)
                }
                is AppResult.Failure -> _uiState.update { it.copy(busy = false, error = r.error.message ?: "Incorrect code — try again") }
            }
        }
    }

    /** The user saved the codes shown on the CODES step. */
    fun doneWithCodes() {
        _uiState.update {
            it.copy(step = MfaStep.OVERVIEW, backupCodes = emptyList(), info = "Two-factor authentication is on, with backup codes saved.")
        }
        refreshCodeStatus()
    }

    fun clearInfo() = _uiState.update { it.copy(info = null) }
}
