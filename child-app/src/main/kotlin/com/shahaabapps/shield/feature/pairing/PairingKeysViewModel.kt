package com.shahaabapps.shield.feature.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.firebase.child.ChildRepository
import com.shahaabapps.core.firebase.e2ee.E2eeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the pairing screen shows: this device's public key (pinned by the parent
 * from the QR) and a server-issued single-use pairing code, refreshed shortly
 * before it expires so the screen always shows a working code.
 */
@HiltViewModel
class PairingKeysViewModel @Inject constructor(
    private val e2eeManager: E2eeManager,
    private val childRepository: ChildRepository,
) : ViewModel() {
    private val _publicKeyB64 = MutableStateFlow<String?>(null)
    val publicKeyB64: StateFlow<String?> = _publicKeyB64.asStateFlow()

    private val _code = MutableStateFlow<String?>(null)
    val code: StateFlow<String?> = _code.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            _publicKeyB64.value = runCatching { e2eeManager.myPublicKeyB64() }.getOrNull()
        }
        viewModelScope.launch {
            while (true) {
                when (val r = childRepository.startPairing()) {
                    is AppResult.Success -> {
                        _code.value = r.data.code
                        _error.value = null
                        // Refresh ~30 s before expiry (codes last ~10 minutes).
                        val ttl = r.data.expiresAtMillis - System.currentTimeMillis()
                        delay((ttl - REFRESH_MARGIN_MS).coerceIn(MIN_REFRESH_MS, MAX_REFRESH_MS))
                    }
                    is AppResult.Failure -> {
                        _code.value = null
                        _error.value = "Couldn't get a pairing code. Check the internet connection."
                        delay(RETRY_MS)
                    }
                }
            }
        }
    }

    private companion object {
        const val REFRESH_MARGIN_MS = 30_000L
        const val MIN_REFRESH_MS = 60_000L
        const val MAX_REFRESH_MS = 9 * 60_000L
        const val RETRY_MS = 15_000L
    }
}
