package com.shahaabapps.shield.messaging

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.messaging.MessagingTokenRepository
import com.shahaabapps.shield.enforcement.EnforcementService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives silent, high-priority data messages for this device. A screenshot
 * request arrives as a data-only message (no notification is ever shown to the
 * child) and triggers an immediate capture via the running accessibility service
 * — this is what makes capture near-instant even when the device is dozing.
 */
@AndroidEntryPoint
class ShieldMessagingService : FirebaseMessagingService() {

    @Inject lateinit var tokenRepository: MessagingTokenRepository
    @Inject lateinit var authRepository: AuthRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val uid = authRepository.currentUserIdOrNull() ?: return
        scope.launch { tokenRepository.registerToken(uid, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        when (message.data["type"]) {
            "screenshot" -> {
                val nonce = message.data["nonce"]?.toLongOrNull() ?: System.currentTimeMillis()
                EnforcementService.instance?.onRemoteCaptureCommand(nonce)
            }
        }
    }
}
