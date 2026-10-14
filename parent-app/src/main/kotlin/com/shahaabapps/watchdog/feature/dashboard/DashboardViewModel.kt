package com.shahaabapps.watchdog.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.child.ChildRepository
import com.shahaabapps.core.model.ChildAccount
import com.shahaabapps.core.model.PairingCodec
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed interface LinkState {
    data object Idle : LinkState
    data object Linking : LinkState
    data class Success(val childName: String?) : LinkState
    data class Error(val message: String) : LinkState
}

sealed interface ReconnectState {
    data object Idle : ReconnectState
    data object Working : ReconnectState
    data object Success : ReconnectState
    data class Error(val message: String) : ReconnectState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val childRepository: ChildRepository,
) : ViewModel() {

    val children: StateFlow<List<ChildAccount>> = authRepository.currentUserId
        .flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList()) else childRepository.observeLinkedChildren(uid)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * True until the first children snapshot arrives for the current user, so the
     * UI shows a spinner — not the empty/first-run "add a child" screen — during
     * the brief gap right after sign-in before the children query responds.
     */
    val loading: StateFlow<Boolean> = authRepository.currentUserId
        .flatMapLatest { uid ->
            if (uid == null) flowOf(true) else childRepository.observeLinkedChildren(uid).map { false }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
        )

    private val _linkState = MutableStateFlow<LinkState>(LinkState.Idle)
    val linkState: StateFlow<LinkState> = _linkState.asStateFlow()

    fun onQrScanned(contents: String?) {
        val pairing = PairingCodec.decode(contents)
        if (pairing == null) {
            _linkState.value = LinkState.Error("That QR code isn't a valid pairing code.")
            return
        }
        linkWith(pairing.childId, pairing.code)
    }

    fun onManualCode(childId: String, code: String) = linkWith(childId, code.trim())

    fun onImageDecodeFailed() {
        _linkState.value = LinkState.Error("No QR code found in that image.")
    }

    private fun linkWith(childId: String, code: String) {
        val parentId = authRepository.currentUserIdOrNull() ?: return
        _linkState.value = LinkState.Linking
        viewModelScope.launch {
            _linkState.value = when (val result = childRepository.linkChild(childId, code, parentId)) {
                is AppResult.Success -> LinkState.Success(null)
                is AppResult.Failure -> LinkState.Error(result.error.message ?: "Couldn't link this device.")
            }
        }
    }

    fun clearLinkState() {
        _linkState.value = LinkState.Idle
    }

    /** Signs the parent out; the app returns to the auth flow. */
    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    /** Unlinks (removes) a child from this parent. */
    fun removeChild(childId: String) {
        val parentId = authRepository.currentUserIdOrNull() ?: return
        viewModelScope.launch { childRepository.unlinkChild(childId, parentId) }
    }

    private val _reconnectState = MutableStateFlow<ReconnectState>(ReconnectState.Idle)
    val reconnectState: StateFlow<ReconnectState> = _reconnectState.asStateFlow()

    /**
     * Reconnect a reinstalled Shield: [oldChildId] is the stale child in the list,
     * [contents] is the QR scanned from the freshly reinstalled device. A Cloud
     * Function migrates the old data onto the new device's id.
     */
    fun reconnectChild(oldChildId: String, contents: String?) {
        val pairing = PairingCodec.decode(contents)
        if (pairing == null) {
            _reconnectState.value = ReconnectState.Error("That QR code isn't a valid pairing code.")
            return
        }
        _reconnectState.value = ReconnectState.Working
        viewModelScope.launch {
            val data = hashMapOf(
                "oldChildId" to oldChildId,
                "newChildId" to pairing.childId,
                "code" to pairing.code,
            )
            _reconnectState.value = runCatching {
                FirebaseFunctions.getInstance().getHttpsCallable("reconnectChild").call(data).await()
            }.fold(
                onSuccess = { ReconnectState.Success },
                onFailure = { ReconnectState.Error(it.message ?: "Couldn't reconnect this device.") },
            )
        }
    }

    fun clearReconnectState() { _reconnectState.value = ReconnectState.Idle }
}
