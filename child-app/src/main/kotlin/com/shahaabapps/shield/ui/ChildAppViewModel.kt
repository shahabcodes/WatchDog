package com.shahaabapps.shield.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.child.ChildRepository
import com.shahaabapps.core.model.ChildAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
) : ViewModel() {

    private val authError = MutableStateFlow<String?>(null)

    init {
        ensureSignedIn()
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
        child == null || child.profile.name.isBlank() -> ChildAppState.NeedsProfile(childId)
        child.isLinked -> ChildAppState.Protected(child)
        else -> ChildAppState.Pairing(child)
    }
}
