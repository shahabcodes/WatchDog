package com.shahaabapps.shield.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.messaging.MessagingTokenRepository
import com.shahaabapps.shield.MainActivity
import com.shahaabapps.shield.PermissionNavigator
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
        com.shahaabapps.core.common.Diag.info("push", "received type=${message.data["type"] ?: "notification"}")
        when (message.data["type"]) {
            "screenshot" -> {
                val nonce = message.data["nonce"]?.toLongOrNull() ?: System.currentTimeMillis()
                EnforcementService.instance?.onRemoteCaptureCommand(nonce)
            }
            "permission" -> {
                val permission = message.data["permission"] ?: return
                showPermissionNotification(permission)
            }
        }
    }

    /**
     * Shown when the app is in the foreground (the system shows the tray
     * notification itself when backgrounded). Tapping opens the exact settings
     * screen for [permission] via MainActivity.
     */
    private fun showPermissionNotification(permission: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager?.getNotificationChannel(CHANNEL_ID) == null) {
                manager?.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Permission requests", NotificationManager.IMPORTANCE_HIGH)
                        .apply { description = "Prompts from your family to turn on a permission" },
                )
            }
        }
        val tap = PendingIntent.getActivity(
            this,
            permission.hashCode(),
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(PermissionNavigator.EXTRA_PERMISSION, permission),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Action needed")
            .setContentText("Your family asked you to turn on ${PermissionNavigator.label(permission)}. Tap to fix it.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Your family asked you to turn on ${PermissionNavigator.label(permission)}. Tap to fix it."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(PERMISSION_NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val CHANNEL_ID = "permission_requests"
        const val PERMISSION_NOTIFICATION_ID = 51
    }
}
