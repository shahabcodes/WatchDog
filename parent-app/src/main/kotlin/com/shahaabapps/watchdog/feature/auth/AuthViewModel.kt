package com.shahaabapps.watchdog.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.firebase.auth.AuthRepository
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
                    val result = if (current.isSignUp) {
                        authRepository.signUpWithEmail(current.email, current.password)
                    } else {
                        authRepository.signInWithEmail(current.email, current.password)
                    }
                    when (result) {
                        is AppResult.Success -> _uiState.update { it.copy(isSubmitting = false) }
                        is AppResult.Failure -> _uiState.update {
                            it.copy(isSubmitting = false, errorMessage = result.error.message ?: "Authentication failed")
                        }
                    }
                    // On success the top-level ParentAppViewModel reacts to the auth state change.
                }
            }
        }
    }
}
