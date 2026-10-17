package com.shahaabapps.watchdog.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.child.ChildRepository
import com.shahaabapps.core.firebase.e2ee.E2eeManager
import com.shahaabapps.core.model.ChildAccount
import com.shahaabapps.core.model.PairingCodec
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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

sealed interface VerifyState {
    data object Idle : VerifyState
    data object Success : VerifyState
    data class Error(val message: String) : VerifyState
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
    private val e2eeManager: E2eeManager,
) : ViewModel() {

    init {
        // Publish this parent's E2EE public key so linked children can wrap their
        // data key for it. Idempotent; safe on every launch.
        viewModelScope.launch {
            val uid = authRepository.currentUserId.filterNotNull().first()
            runCatching { e2eeManager.publishParentKey(uid) }
        }
    }

    val children: StateFlow<List<ChildAccount>> = authRepository.currentUserId
        .flatMapLatest { uid ->
            when {
                uid == null -> flowOf(emptyList())
                // A viewer (anonymous) session observes the children it may view.
                authRepository.isAnonymous() -> childRepository.observeViewableChildren(uid)
                else -> childRepository.observeLinkedChildren(uid)
            }
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
            when {
                uid == null -> flowOf(true)
                authRepository.isAnonymous() -> childRepository.observeViewableChildren(uid).map { false }
                else -> childRepository.observeLinkedChildren(uid).map { false }
            }
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
        linkWith(pairing.childId, pairing.code, pairing.childPublicKey)
    }

    fun onManualCode(childId: String, code: String) = linkWith(childId, code.trim())

    fun onImageDecodeFailed() {
        _linkState.value = LinkState.Error("No QR code found in that image.")
    }

    private fun linkWith(childId: String, code: String, childPublicKey: String? = null) {
        val parentId = authRepository.currentUserIdOrNull() ?: return
        _linkState.value = LinkState.Linking
        viewModelScope.launch {
            _linkState.value = when (val result = childRepository.linkChild(childId, code, parentId)) {
                is AppResult.Success -> {
                    // Pin the child's key captured out-of-band from the QR, so this
                    // link is verified independently of the backend.
                    childPublicKey?.let { runCatching { e2eeManager.pinChildKey(childId, it) } }
                    LinkState.Success(null)
                }
                is AppResult.Failure -> LinkState.Error(result.error.message ?: "Couldn't link this device.")
            }
        }
    }

    private val _verifyState = MutableStateFlow<VerifyState>(VerifyState.Idle)
    val verifyState: StateFlow<VerifyState> = _verifyState.asStateFlow()

    /** Returns whether [childId]'s key has been pinned (verified out-of-band). */
    fun keyVerified(childId: String): kotlinx.coroutines.flow.Flow<Boolean> =
        e2eeManager.observeChildKeyVerified(childId).map { it != null }

    /**
     * Re-scan flow for an already-linked child: pins the key from the QR without
     * re-linking (the child is no longer in pairing mode). [expectedChildId], when
     * given, guards against scanning the wrong device's code.
     */
    fun verifyKeyFromQr(contents: String?, expectedChildId: String? = null) {
        val pairing = PairingCodec.decode(contents)
        val key = pairing?.childPublicKey
        when {
            pairing == null -> _verifyState.value = VerifyState.Error("That isn't a valid pairing code.")
            expectedChildId != null && pairing.childId != expectedChildId ->
                _verifyState.value = VerifyState.Error("That code is for a different device.")
            key == null -> _verifyState.value =
                VerifyState.Error("This device's app is out of date — update Shield, then try again.")
            else -> viewModelScope.launch {
                runCatching { e2eeManager.pinChildKey(pairing.childId, key) }
                    .onSuccess { _verifyState.value = VerifyState.Success }
                    .onFailure { _verifyState.value = VerifyState.Error("Couldn't verify. Try again.") }
            }
        }
    }

    fun clearVerifyState() { _verifyState.value = VerifyState.Idle }

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
                onSuccess = {
                    // The reinstalled device has a new identity key; pin it from the QR.
                    pairing.childPublicKey?.let { runCatching { e2eeManager.pinChildKey(pairing.childId, it) } }
                    ReconnectState.Success
                },
                onFailure = { ReconnectState.Error(it.message ?: "Couldn't reconnect this device.") },
            )
        }
    }

    fun clearReconnectState() { _reconnectState.value = ReconnectState.Idle }
}
