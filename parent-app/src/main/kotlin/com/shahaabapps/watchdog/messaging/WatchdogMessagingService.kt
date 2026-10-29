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
import com.shahaabapps.core.firebase.blockattempt.BlockAttemptRepository
import com.shahaabapps.core.firebase.geofence.GeofenceNameCache
import com.shahaabapps.core.firebase.geofence.GeofenceRepository
import com.shahaabapps.core.firebase.messaging.MessagingTokenRepository
import com.shahaabapps.core.model.BlockAttemptReason
import com.shahaabapps.watchdog.MainActivity
import com.shahaabapps.watchdog.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Receives geofence, request and blocked-app pushes and keeps the parent's FCM token in Firestore. */
@AndroidEntryPoint
class WatchdogMessagingService : FirebaseMessagingService() {

    @Inject lateinit var tokenRepository: MessagingTokenRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var geofenceRepository: GeofenceRepository
    @Inject lateinit var blockAttemptRepository: BlockAttemptRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val uid = authRepository.currentUserIdOrNull() ?: return
        scope.launch { tokenRepository.registerToken(uid, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        com.shahaabapps.core.common.Diag.info("push", "received type=${message.data["type"] ?: "notification"}")
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
        // The child tried a blocked app: also data-only and content-free (which
        // app is E2E-encrypted), so decrypt the attempt on-device like geofences.
        if (message.data["type"] == "blockAttempt") {
            handleBlockAttemptPush(message.data)
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

    private fun handleBlockAttemptPush(data: Map<String, String>) {
        val childId = data["childId"]?.ifBlank { null } ?: return
        val eventId = data["eventId"]?.ifBlank { null } ?: return
        val childName = data["childName"]?.ifBlank { null } ?: "Your child"
        scope.launch {
            // getEvent warms the reader key first (a cold push process has none).
            val attempt = runCatching { blockAttemptRepository.getEvent(childId, eventId) }.getOrNull()
            val label = attempt?.label?.let(::cleanLabel)
            if (attempt == null || label == null) {
                showBlockAttemptNotification(
                    this@WatchdogMessagingService, childId, null, eventId,
                    "$childName tried to open a blocked app", "Tap to see their app rules.",
                )
            } else {
                val tries = if (attempt.count > 1) " · tried ${attempt.count} times" else ""
                showBlockAttemptNotification(
                    this@WatchdogMessagingService, childId, attempt.packageName.ifBlank { null }, eventId,
                    "$childName tried to open $label", reasonText(attempt.reason) + tries,
                )
            }
            pruneOldAttempts(childId)
        }
    }

    /** Clears this child's attempts older than a week, at most once a day per child. */
    private suspend fun pruneOldAttempts(childId: String) {
        val prefs = getSharedPreferences("block_attempt_prune", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(childId, 0L) < PRUNE_EVERY_MS) return
        prefs.edit().putLong(childId, now).apply()
        blockAttemptRepository.deleteOlderThan(childId, now - KEEP_ATTEMPTS_MS)
    }

    companion object {
        const val CHANNEL_ID = "geofence_alerts"
        const val REQUEST_CHANNEL_ID = "child_requests"
        const val BLOCKED_CHANNEL_ID = "blocked_apps"

        private const val KEEP_ATTEMPTS_MS = 7 * 24 * 60 * 60 * 1000L
        private const val PRUNE_EVERY_MS = 24 * 60 * 60 * 1000L
        private val LINK_RE = Regex("(https?://|www\\.)\\S+", RegexOption.IGNORE_CASE)

        // Intent extras read by MainActivity to deep-link into the right request.
        const val EXTRA_CHILD_ID = "deeplink_child_id"
        const val EXTRA_REQUEST_ID = "deeplink_request_id"
        const val EXTRA_ACTION = "deeplink_action"
        const val ACTION_VIEW = "VIEW"
        const val ACTION_APPROVE = "APPROVE"
        const val ACTION_REJECT = "REJECT"
        // A blocked-app alert: opens the child's Activity tab on that app's rule.
        const val ACTION_APP_RULES = "APP_RULES"
        const val EXTRA_APP_PACKAGE = "deeplink_app_package"

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
            if (manager.getNotificationChannel(BLOCKED_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        BLOCKED_CHANNEL_ID,
                        "Blocked apps",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply { description = "When your child tries to open a blocked app." },
                )
            }
        }

        /** The notification's second line: why the app was blocked. */
        private fun reasonText(reason: BlockAttemptReason): String = when (reason) {
            BlockAttemptReason.BLOCKED -> "Blocked by you"
            BlockAttemptReason.LIMIT -> "Daily limit reached"
            BlockAttemptReason.HOURS -> "Outside allowed hours"
            BlockAttemptReason.BEDTIME -> "Bedtime"
            BlockAttemptReason.SCHOOL -> "School"
            BlockAttemptReason.REVIEW -> "Waiting for your review"
            BlockAttemptReason.INSTALL_GUARD -> "Not installed from the Play Store"
            BlockAttemptReason.MULTI_WINDOW -> "Pop-up and split-screen view are off"
        }

        /**
         * The app's name comes from the child's phone: one line, no links, short, so
         * a tampered phone can't make the alert look like a sign-in prompt.
         */
        private fun cleanLabel(label: String): String? {
            val text = label.replace(LINK_RE, "").replace(Regex("\\s+"), " ").trim()
            if (text.isEmpty()) return null
            return if (text.length > 60) text.take(59) + "…" else text
        }

        /** Blocked-app alert; a tap opens [childId]'s Activity tab on [appPackage]'s rule. */
        fun showBlockAttemptNotification(
            context: Context,
            childId: String,
            appPackage: String?,
            eventId: String,
            title: String,
            body: String,
        ) {
            ensureChannel(context)
            val intent = Intent(context, MainActivity::class.java)
                .setAction("$ACTION_APP_RULES:$childId:${appPackage.orEmpty()}") // distinct PendingIntent per app
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_CHILD_ID, childId)
                .putExtra(EXTRA_ACTION, ACTION_APP_RULES)
                .apply { if (appPackage != null) putExtra(EXTRA_APP_PACKAGE, appPackage) }
            val pending = PendingIntent.getActivity(
                context,
                intent.action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, BLOCKED_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            // One notification per child and app: a later attempt replaces it.
            val tag = "blocked:$childId:${appPackage ?: eventId}"
            runCatching { NotificationManagerCompat.from(context).notify(tag, 0, notification) }
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
