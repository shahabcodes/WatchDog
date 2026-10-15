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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Builds the "secure code" QR the parent re-scans to verify this device's E2EE
 * key out-of-band. Same payload as the pairing QR (childId + code + public key),
 * available while the device is already linked.
 */
@HiltViewModel
class SecureCodeViewModel @Inject constructor(
    authRepository: AuthRepository,
    childRepository: ChildRepository,
    private val e2eeManager: E2eeManager,
) : ViewModel() {

    private val childId: String? = authRepository.currentUserIdOrNull()

    val qrContent: StateFlow<String?> =
        (if (childId == null) flowOf(null) else childRepository.observeChild(childId))
            .map { child ->
                val pub = runCatching { e2eeManager.myPublicKeyB64() }.getOrNull()
                if (childId == null || child == null || pub == null) null
                else PairingCodec.encode(PairingData(childId, child.pairingCode, pub))
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
