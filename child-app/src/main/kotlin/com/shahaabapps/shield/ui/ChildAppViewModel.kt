package com.shahaabapps.shield.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.child.ChildRepository
import com.shahaabapps.core.firebase.e2ee.E2eeManager
import com.shahaabapps.core.firebase.messaging.MessagingTokenRepository
import com.shahaabapps.core.model.ChildAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed interface ChildAppState {
    data object Loading : ChildAppState
    data class Error(val message: String) : ChildAppState
    data class NeedsProfile(val childId: String) : ChildAppState
    data class Pairing(val child: ChildAccount) : ChildAppState
    data class Protected(val child: ChildAccount) : ChildAppState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChildAppViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val childRepository: ChildRepository,
    private val messagingTokenRepository: MessagingTokenRepository,
    private val e2eeManager: E2eeManager,
) : ViewModel() {

    private val authError = MutableStateFlow<String?>(null)

    init {
        ensureSignedIn()
        registerFcmToken()
        syncE2eeKeys()
    }

    /**
     * Publishes this child's E2EE public key and ensures every linked parent has
     * a wrapped copy of the data key. Runs on each child snapshot (not debounced to
     * the parent list) so a parent that publishes its key *after* being linked —
     * e.g. an existing family upgrading — is picked up on the next heartbeat.
     * E2eeManager makes repeat calls near-free (publish-once + already-wrapped
     * short-circuit), so this doesn't amplify writes.
     */
    private fun syncE2eeKeys() {
        viewModelScope.launch {
            authRepository.currentUserId.filterNotNull()
                .flatMapLatest { uid ->
                    childRepository.observeChild(uid)
                        .map { it?.linkedParents ?: emptyList() }
                }
                .collect { parents ->
                    val uid = authRepository.currentUserIdOrNull() ?: return@collect
                    runCatching { e2eeManager.syncChildKeys(uid, parents) }
                }
        }
    }

    /**
     * Stores this device's FCM token under its own user id so the screenshot Cloud
     * Function can send it an instant, silent capture push.
     */
    private fun registerFcmToken() {
        viewModelScope.launch {
            val uid = authRepository.currentUserId.filterNotNull().first()
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                messagingTokenRepository.registerToken(uid, token)
            }
        }
    }

    private fun ensureSignedIn() {
        if (authRepository.currentUserIdOrNull() != null) return
        viewModelScope.launch {
            when (val result = authRepository.signInAnonymously()) {
                is AppResult.Success -> authError.value = null
                is AppResult.Failure -> authError.value =
                    "Couldn't connect. Check your internet and Firebase setup, then retry."
            }
        }
    }

    fun retry() {
        authError.value = null
        ensureSignedIn()
    }

    val state: StateFlow<ChildAppState> =
        combine(authRepository.currentUserId, authError) { uid, error -> uid to error }
            .flatMapLatest { (uid, error) ->
                when {
                    uid != null -> childRepository.observeChild(uid)
                        .map { child -> mapChildState(uid, child) }
                        .catch { emit(ChildAppState.Error("Couldn't load your profile. Retry.")) }
                    error != null -> flowOf(ChildAppState.Error(error))
                    else -> flowOf(ChildAppState.Loading)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ChildAppState.Loading,
            )

    private fun mapChildState(childId: String, child: ChildAccount?): ChildAppState = when {
        child == null -> ChildAppState.NeedsProfile(childId)
        child.isLinked -> ChildAppState.Protected(child)
        // Any existing, unlinked record goes to the pairing screen (which fetches a
        // server-issued code) — including a reconnect, whose record has no name yet
        // because the name/DOB arrive via migration.
        else -> ChildAppState.Pairing(child)
    }
}
