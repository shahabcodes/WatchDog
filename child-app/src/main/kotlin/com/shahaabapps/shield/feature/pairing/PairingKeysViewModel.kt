package com.shahaabapps.shield.feature.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.firebase.e2ee.E2eeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Exposes this device's E2EE public key (base64) so the pairing QR can carry it
 * to the parent out-of-band. Computed off the main thread because the first
 * access may generate the device keypair.
 */
@HiltViewModel
class PairingKeysViewModel @Inject constructor(
    private val e2eeManager: E2eeManager,
) : ViewModel() {
    private val _publicKeyB64 = MutableStateFlow<String?>(null)
    val publicKeyB64: StateFlow<String?> = _publicKeyB64.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            _publicKeyB64.value = runCatching { e2eeManager.myPublicKeyB64() }.getOrNull()
        }
    }
}
