package com.shahaabapps.watchdog.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.child.ChildRepository
import com.shahaabapps.core.model.ChildAccount
import com.shahaabapps.core.model.PairingCodec
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
import javax.inject.Inject

sealed interface LinkState {
    data object Idle : LinkState
    data object Linking : LinkState
    data class Success(val childName: String?) : LinkState
    data class Error(val message: String) : LinkState
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

    /** True until the first children snapshot arrives, so the UI can show a spinner. */
    val loading: StateFlow<Boolean> = authRepository.currentUserId
        .flatMapLatest { uid ->
            if (uid == null) flowOf(false) else childRepository.observeLinkedChildren(uid).map { false }
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
}
