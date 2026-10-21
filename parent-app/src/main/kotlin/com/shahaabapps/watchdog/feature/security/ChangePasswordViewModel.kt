package com.shahaabapps.watchdog.feature.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.auth.PasswordRule
import com.shahaabapps.core.firebase.auth.SignInOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which screen of the change-password flow is showing. */
enum class ChangePasswordStep { FORM, CODE, DONE }

data class ChangePasswordUiState(
    val step: ChangePasswordStep = ChangePasswordStep.FORM,
    val current: String = "",
    val newPassword: String = "",
    val confirm: String = "",
    val code: String = "",
    val busy: Boolean = false,
    val error: String? = null,
) {
    /** What the new password still needs (null while empty or once it meets the rule). */
    val newProblem: String? get() = if (newPassword.isEmpty()) null else PasswordRule.problem(newPassword)
    val mismatch: Boolean get() = confirm.isNotEmpty() && confirm != newPassword
    val canSubmit: Boolean
        get() = !busy && current.isNotEmpty() && newPassword.isNotEmpty() &&
            PasswordRule.problem(newPassword) == null && confirm == newPassword
}

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    fun onCurrent(value: String) = _uiState.update { it.copy(current = value, error = null) }
    fun onNew(value: String) = _uiState.update { it.copy(newPassword = value, error = null) }
    fun onConfirm(value: String) = _uiState.update { it.copy(confirm = value, error = null) }
    fun onCode(value: String) = _uiState.update { it.copy(code = value.filter { c -> c.isDigit() }.take(6), error = null) }

    /** Confirm the current password; with 2FA on, continue to the code step. */
    fun submit() {
        val s = _uiState.value
        if (!s.canSubmit) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = authRepository.reauthenticate(s.current)) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(busy = false, error = "Your current password is incorrect.")
                }
                is AppResult.Success -> when (r.data) {
                    is SignInOutcome.Success -> change()
                    SignInOutcome.MfaRequired -> _uiState.update {
                        it.copy(busy = false, step = ChangePasswordStep.CODE, code = "")
                    }
                }
            }
        }
    }

    fun submitCode() {
        val s = _uiState.value
        if (s.busy || s.code.length != 6) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = authRepository.resolveMfa(s.code)) {
                is AppResult.Success -> change()
                is AppResult.Failure -> _uiState.update { it.copy(busy = false, error = r.error.message ?: "Incorrect code") }
            }
        }
    }

    private suspend fun change() {
        when (val r = authRepository.changePassword(_uiState.value.newPassword)) {
            is AppResult.Success -> _uiState.update {
                ChangePasswordUiState(step = ChangePasswordStep.DONE)
            }
            is AppResult.Failure -> _uiState.update {
                it.copy(
                    busy = false,
                    step = ChangePasswordStep.FORM,
                    error = PasswordRule.friendlyError(r.error) ?: r.error.message ?: "Couldn't change your password",
                )
            }
        }
    }
}
