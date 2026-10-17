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
    /** Anonymous read-only observer (joined with a pairing code). */
    data object Viewer : ParentAppState
}

/** Progress of a "join as viewer" attempt. */
sealed interface ViewerJoinState {
    data object Idle : ViewerJoinState
    data object Joining : ViewerJoinState
    data class Error(val message: String) : ViewerJoinState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ParentAppViewModel @Inject constructor(
    private val authRepository: AuthRepository,
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
            when {
                userId == null -> flowOf(ParentAppState.SignedOut)
                // Anonymous session = a read-only viewer (joined with a pairing code).
                authRepository.isAnonymous() -> flowOf(ParentAppState.Viewer)
                else -> profileRepository.observeProfile(userId).map { profile ->
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

    private val _joinState = kotlinx.coroutines.flow.MutableStateFlow<ViewerJoinState>(ViewerJoinState.Idle)
    val joinState: StateFlow<ViewerJoinState> = _joinState

    /**
     * Join a child as a read-only viewer with its pairing [code]. Runs on the
     * app-scoped VM so the sign-in + function call survive the screen swap that
     * anonymous sign-in triggers.
     */
    fun joinAsViewer(code: String) {
        if (_joinState.value == ViewerJoinState.Joining) return
        _joinState.value = ViewerJoinState.Joining
        viewModelScope.launch {
            // Ensure signed in (anonymously) so the callable has an auth context.
            if (authRepository.currentUserIdOrNull() == null) {
                authRepository.signInAnonymously()
            }
            val result = runCatching {
                com.google.firebase.functions.FirebaseFunctions.getInstance()
                    .getHttpsCallable("joinAsViewer")
                    .call(hashMapOf("code" to code.trim()))
                    .await()
            }
            result.onSuccess {
                _joinState.value = ViewerJoinState.Idle // state flips to Viewer via auth flow
            }.onFailure { e ->
                // Roll back the anonymous session so the user returns to sign-in.
                runCatching { authRepository.signOut() }
                _joinState.value = ViewerJoinState.Error(e.message ?: "Couldn't join. Check the code.")
            }
        }
    }

    fun clearJoinError() { _joinState.value = ViewerJoinState.Idle }
}
