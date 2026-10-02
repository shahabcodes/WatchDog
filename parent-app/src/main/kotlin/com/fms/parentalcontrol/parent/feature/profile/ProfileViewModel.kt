package com.fms.parentalcontrol.parent.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fms.parentalcontrol.core.common.AppResult
import com.fms.parentalcontrol.core.firebase.auth.AuthRepository
import com.fms.parentalcontrol.core.firebase.profile.ProfileRepository
import com.fms.parentalcontrol.core.model.UserProfile
import com.fms.parentalcontrol.core.model.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val name: String = "",
    val photoUrl: String? = null,
    val dateOfBirthMillis: Long? = null,
    val isSaving: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSave: Boolean get() = name.isNotBlank() && !isSaving
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, errorMessage = null) }

    fun onDateOfBirthSelected(millis: Long) = _uiState.update { it.copy(dateOfBirthMillis = millis) }

    fun onPhotoPicked(bytes: ByteArray) {
        val userId = authRepository.currentUserIdOrNull() ?: return
        _uiState.update { it.copy(isUploadingPhoto = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = profileRepository.uploadAvatar(userId, bytes)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(isUploadingPhoto = false, photoUrl = result.data)
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isUploadingPhoto = false, errorMessage = "Photo upload failed")
                }
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        val userId = authRepository.currentUserIdOrNull() ?: return
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val profile = UserProfile(
                id = userId,
                name = state.name.trim(),
                photoUrl = state.photoUrl,
                dateOfBirthMillis = state.dateOfBirthMillis,
                role = UserRole.PARENT,
            )
            when (val result = profileRepository.saveProfile(profile)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    onSaved()
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, errorMessage = result.error.message ?: "Could not save profile")
                }
            }
        }
    }
}
