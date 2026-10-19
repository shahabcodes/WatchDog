package com.shahaabapps.shield.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.child.ChildRepository
import com.shahaabapps.core.firebase.e2ee.E2eeManager
import com.shahaabapps.core.model.PairingCodec
import com.shahaabapps.core.model.PairingData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Builds the "secure code" QR the parent re-scans to verify this device's E2EE
 * key out-of-band. Same payload as the pairing QR (childId + code + public key),
 * available while the device is already linked. Also exposes a manual "re-sync
 * security" action that re-wraps the data key for every linked parent now.
 */
@HiltViewModel
class SecureCodeViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val childRepository: ChildRepository,
    private val e2eeManager: E2eeManager,
) : ViewModel() {

    private val childId: String? = authRepository.currentUserIdOrNull()

    val qrContent: StateFlow<String?> =
        (if (childId == null) flowOf(null) else childRepository.observeChild(childId))
            .map { child ->
                val pub = runCatching { e2eeManager.myPublicKeyB64() }.getOrNull()
                if (childId == null || child == null || pub == null) null
                // Key-verification QR only: the parent pins the key and ignores the
                // code, so a fixed placeholder (pairing codes are server-issued now).
                else PairingCodec.encode(PairingData(childId, VERIFY_ONLY_CODE, pub))
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _resyncBusy = MutableStateFlow(false)
    val resyncBusy: StateFlow<Boolean> = _resyncBusy

    /**
     * Re-share this device's encryption key with every linked parent now, so a
     * parent stuck on "can't see location/history" recovers without re-pairing.
     */
    fun resyncSecurity() {
        val id = childId ?: return
        if (_resyncBusy.value) return
        viewModelScope.launch {
            _resyncBusy.value = true
            runCatching {
                val parents = childRepository.observeChild(id).first()?.linkedParents ?: emptyList()
                e2eeManager.reconcileAsChild(id, parents)
            }
            kotlinx.coroutines.delay(1_500)
            _resyncBusy.value = false
        }
    }
}

private const val VERIFY_ONLY_CODE = "verify"
