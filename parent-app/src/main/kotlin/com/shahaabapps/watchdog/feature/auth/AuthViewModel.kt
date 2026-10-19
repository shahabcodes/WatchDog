package com.shahaabapps.watchdog.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.auth.SignInOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode { SIGN_IN, SIGN_UP, FORGOT }

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val mode: AuthMode = AuthMode.SIGN_IN,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    /** Sign-in hit a TOTP second factor; show the 6-digit code step. */
    val mfaRequired: Boolean = false,
    val mfaCode: String = "",
    /** On the MFA step: signing in with a backup code instead of the authenticator. */
    val useBackupCode: Boolean = false,
    val backupCode: String = "",
) {
    val isSignUp: Boolean get() = mode == AuthMode.SIGN_UP
    val isForgot: Boolean get() = mode == AuthMode.FORGOT

    /** True only while signing up with two passwords that don't match. */
    val passwordsMismatch: Boolean
        get() = isSignUp && confirmPassword.isNotEmpty() && confirmPassword != password

    val canSubmit: Boolean
        get() = when {
            isSubmitting -> false
            isForgot -> email.contains("@")
            isSignUp -> email.contains("@") && password.length >= 6 && confirmPassword == password
            else -> email.contains("@") && password.length >= 6
        }
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) =
        _uiState.update { it.copy(email = value.trim(), errorMessage = null, infoMessage = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value, errorMessage = null, infoMessage = null) }

    fun onConfirmPasswordChange(value: String) =
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null, infoMessage = null) }

    fun setMode(mode: AuthMode) = _uiState.update {
        it.copy(mode = mode, confirmPassword = "", errorMessage = null, infoMessage = null)
    }

    fun submit() {
        val current = _uiState.value
        if (!current.canSubmit) return
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null, infoMessage = null) }

        viewModelScope.launch {
            when (current.mode) {
                AuthMode.FORGOT -> {
                    when (val result = authRepository.sendPasswordReset(current.email)) {
                        is AppResult.Success -> _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                infoMessage = "Reset link sent to ${current.email}",
                            )
                        }
                        is AppResult.Failure -> _uiState.update {
                            it.copy(isSubmitting = false, errorMessage = result.error.message ?: "Couldn't send reset email")
                        }
                    }
                }
                else -> {
                    if (current.isSignUp) {
                        when (val result = authRepository.signUpWithEmail(current.email, current.password)) {
                            is AppResult.Success -> _uiState.update { it.copy(isSubmitting = false) }
                            is AppResult.Failure -> _uiState.update {
                                it.copy(isSubmitting = false, errorMessage = result.error.message ?: "Authentication failed")
                            }
                        }
                    } else {
                        when (val result = authRepository.signInWithEmail(current.email, current.password)) {
                            is AppResult.Success -> when (result.data) {
                                is SignInOutcome.Success -> _uiState.update { it.copy(isSubmitting = false) }
                                SignInOutcome.MfaRequired -> _uiState.update {
                                    it.copy(isSubmitting = false, mfaRequired = true, mfaCode = "")
                                }
                            }
                            is AppResult.Failure -> _uiState.update {
                                it.copy(isSubmitting = false, errorMessage = result.error.message ?: "Authentication failed")
                            }
                        }
                    }
                    // On success the top-level ParentAppViewModel reacts to the auth state change.
                }
            }
        }
    }

    fun onMfaCodeChange(value: String) =
        _uiState.update { it.copy(mfaCode = value.filter { c -> c.isDigit() }.take(6), errorMessage = null) }

    fun onBackupCodeChange(value: String) =
        _uiState.update { it.copy(backupCode = value.take(20), errorMessage = null) }

    /** Switch the MFA step between the authenticator code and a backup code. */
    fun toggleBackupCode() =
        _uiState.update { it.copy(useBackupCode = !it.useBackupCode, mfaCode = "", backupCode = "", errorMessage = null) }

    /** Cancel the MFA step and return to the sign-in form. */
    fun cancelMfa() = _uiState.update {
        it.copy(mfaRequired = false, mfaCode = "", useBackupCode = false, backupCode = "", errorMessage = null)
    }

    /** Submit the 6-digit authenticator code (or a backup code) to finish an MFA-gated sign-in. */
    fun submitMfa() {
        if (_uiState.value.useBackupCode) return submitBackupCode()
        val code = _uiState.value.mfaCode
        if (code.length != 6 || _uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = authRepository.resolveMfa(code)) {
                is AppResult.Success -> _uiState.update { it.copy(isSubmitting = false, mfaRequired = false) }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = result.error.message ?: "Incorrect code")
                }
            }
        }
    }

    /**
     * Lost authenticator: a backup code (with the password already entered) turns
     * 2FA off server-side and signs in. The shell then prompts to set 2FA up again.
     */
    private fun submitBackupCode() {
        val s = _uiState.value
        if (s.backupCode.isBlank() || s.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = authRepository.signInWithBackupCode(s.email, s.password, s.backupCode)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(isSubmitting = false, mfaRequired = false, useBackupCode = false, backupCode = "")
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = result.error.message ?: "Wrong backup code")
                }
            }
        }
    }
}
