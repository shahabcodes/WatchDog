package com.shahaabapps.watchdog.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.geofence.GeofenceRepository
import com.shahaabapps.core.firebase.messaging.MessagingTokenRepository
import com.shahaabapps.watchdog.MainActivity
import com.shahaabapps.watchdog.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Receives geofence push messages and keeps the parent's FCM token in Firestore. */
@AndroidEntryPoint
class WatchdogMessagingService : FirebaseMessagingService() {

    @Inject lateinit var tokenRepository: MessagingTokenRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var geofenceRepository: GeofenceRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val uid = authRepository.currentUserIdOrNull() ?: return
        scope.launch { tokenRepository.registerToken(uid, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Geofence alerts arrive data-only and content-free: the place name is
        // E2E-encrypted, so decrypt the event on-device, then post the notification.
        if (message.data["type"] == "geofence") {
            handleGeofencePush(message.data)
            return
        }
        // Foreground messages (and other data-only messages) land here; post manually.
        val notif = message.notification
        val title = notif?.title ?: message.data["title"] ?: "Watch Dog"
        val body = notif?.body ?: message.data["body"] ?: return
        showNotification(this, title, body)
    }

    private fun handleGeofencePush(data: Map<String, String>) {
        val childId = data["childId"] ?: return
        val eventId = data["eventId"] ?: return
        val childName = data["childName"]?.ifBlank { null } ?: "Your child"
        val left = data["transition"] == "EXIT"
        scope.launch {
            val place = runCatching { geofenceRepository.getEvent(childId, eventId) }
                .getOrNull()?.geofenceName?.ifBlank { null } ?: "a saved place"
            val verb = if (left) "left" else "arrived at"
            showNotification(this@WatchdogMessagingService, "$childName $verb $place", "$childName just $verb $place.")
        }
    }

    companion object {
        const val CHANNEL_ID = "geofence_alerts"

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Place alerts",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply { description = "Alerts when your child enters or leaves a saved place." },
                )
            }
        }

        fun showNotification(context: Context, title: String, body: String) {
            ensureChannel(context)
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pending)
                .build()
            runCatching {
                NotificationManagerCompat.from(context)
                    .notify(title.hashCode(), notification)
            }
        }
    }
}
