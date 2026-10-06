package com.shahaabapps.watchdog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.messaging.MessagingTokenRepository
import com.shahaabapps.core.firebase.profile.ProfileRepository
import com.shahaabapps.core.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/** Top-level UI state that decides which screen the parent app shows. */
sealed interface ParentAppState {
    data object Loading : ParentAppState
    data object SignedOut : ParentAppState
    data class NeedsProfile(val userId: String) : ParentAppState
    data class Ready(val profile: UserProfile) : ParentAppState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ParentAppViewModel @Inject constructor(
    authRepository: AuthRepository,
    profileRepository: ProfileRepository,
    private val messagingTokenRepository: MessagingTokenRepository,
) : ViewModel() {

    /** Stores this device's FCM token so the geofence function can push to it. */
    fun registerFcmToken(userId: String) {
        viewModelScope.launch {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                messagingTokenRepository.registerToken(userId, token)
            }
        }
    }

    val state: StateFlow<ParentAppState> = authRepository.currentUserId
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf(ParentAppState.SignedOut)
            } else {
                profileRepository.observeProfile(userId).map { profile ->
                    if (profile == null || profile.name.isBlank()) {
                        ParentAppState.NeedsProfile(userId)
                    } else {
                        ParentAppState.Ready(profile)
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ParentAppState.Loading,
        )
}
