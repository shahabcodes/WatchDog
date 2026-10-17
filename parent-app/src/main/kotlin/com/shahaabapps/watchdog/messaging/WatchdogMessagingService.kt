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
import com.shahaabapps.core.firebase.geofence.GeofenceNameCache
import com.shahaabapps.core.firebase.geofence.GeofenceRepository
import com.shahaabapps.core.firebase.messaging.MessagingTokenRepository
import com.shahaabapps.watchdog.MainActivity
import com.shahaabapps.watchdog.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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
        // A child's "ask" (unlock an app, more time…): post an interactive
        // notification with Approve/Reject buttons that deep-link into the app.
        if (message.data["type"] == "request") {
            handleRequestPush(message.data)
            return
        }
        // Foreground messages (and other data-only messages) land here; post manually.
        val notif = message.notification
        val title = notif?.title ?: message.data["title"] ?: "Watch Dog"
        val body = notif?.body ?: message.data["body"] ?: return
        showNotification(this, title, body)
    }

    private fun handleRequestPush(data: Map<String, String>) {
        val childId = data["childId"]?.ifBlank { null } ?: return
        val requestId = data["requestId"]?.ifBlank { null } ?: return
        val title = data["title"]?.ifBlank { null } ?: "A new request"
        val body = data["body"]?.ifBlank { null } ?: "Tap to approve or deny."
        showRequestNotification(this, childId, requestId, title, body)
    }

    private fun handleGeofencePush(data: Map<String, String>) {
        val childId = data["childId"] ?: return
        val eventId = data["eventId"] ?: return
        val childName = data["childName"]?.ifBlank { null } ?: "Your child"
        val left = data["transition"] == "EXIT"
        scope.launch {
            val event = runCatching { geofenceRepository.getEvent(childId, eventId) }.getOrNull()
            val gid = (event?.geofenceId ?: data["geofenceId"])?.ifBlank { null }
            // Place names are E2E-encrypted and a cold push process can't always
            // decrypt them. Resolve the name in order of reliability:
            //  1) the on-device name cache (written when the app last decrypted it),
            //  2) the event's own (decrypted) name,
            //  3) a live lookup of the saved places by id,
            // then fall back to generic text.
            val place = gid?.let { GeofenceNameCache.get(this@WatchdogMessagingService, childId, it) }
                ?: event?.geofenceName?.ifBlank { null }
                ?: gid?.let { id ->
                    runCatching {
                        geofenceRepository.observeGeofences(childId).first()
                            .firstOrNull { it.id == id }?.name?.ifBlank { null }
                    }.getOrNull()
                }
                ?: "a saved place"
            val verb = if (left) "left" else "arrived at"
            showNotification(this@WatchdogMessagingService, "$childName $verb $place", "$childName just $verb $place.")
        }
    }

    companion object {
        const val CHANNEL_ID = "geofence_alerts"
        const val REQUEST_CHANNEL_ID = "child_requests"

        // Intent extras read by MainActivity to deep-link into the right request.
        const val EXTRA_CHILD_ID = "deeplink_child_id"
        const val EXTRA_REQUEST_ID = "deeplink_request_id"
        const val EXTRA_ACTION = "deeplink_action"
        const val ACTION_VIEW = "VIEW"
        const val ACTION_APPROVE = "APPROVE"
        const val ACTION_REJECT = "REJECT"

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

        private fun ensureRequestChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(REQUEST_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        REQUEST_CHANNEL_ID,
                        "Child requests",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply { description = "When your child asks to unlock an app or for more time." },
                )
            }
        }

        /** PendingIntent that opens the app on the request, pre-selecting [action]. */
        private fun requestIntent(
            context: Context,
            childId: String,
            requestId: String,
            action: String,
        ): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .setAction("$action:$requestId") // distinct action → distinct PendingIntent
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_CHILD_ID, childId)
                .putExtra(EXTRA_REQUEST_ID, requestId)
                .putExtra(EXTRA_ACTION, action)
            return PendingIntent.getActivity(
                context,
                (action + requestId).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /** Interactive "child request" notification: tap to view, or Approve / Reject. */
        fun showRequestNotification(
            context: Context,
            childId: String,
            requestId: String,
            title: String,
            body: String,
        ) {
            ensureRequestChannel(context)
            val notification = NotificationCompat.Builder(context, REQUEST_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(requestIntent(context, childId, requestId, ACTION_VIEW))
                .addAction(0, "Approve", requestIntent(context, childId, requestId, ACTION_APPROVE))
                .addAction(0, "Reject", requestIntent(context, childId, requestId, ACTION_REJECT))
                .build()
            runCatching {
                NotificationManagerCompat.from(context).notify(requestId.hashCode(), notification)
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
