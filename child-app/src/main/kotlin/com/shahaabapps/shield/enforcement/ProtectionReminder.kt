package com.shahaabapps.shield.enforcement

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.shahaabapps.shield.MainActivity
import com.shahaabapps.shield.PermissionNavigator
import com.shahaabapps.shield.R

/**
 * A notification that stays while uninstall protection (device admin) is off, so
 * an unfinished setup can't go unnoticed on the child's phone. Tapping it opens
 * the device-admin screen. The parent is told separately (Watch Dog, and a
 * daily reminder from the server).
 */
object ProtectionReminder {
    private const val CHANNEL_ID = "protection_setup"
    private const val NOTIFICATION_ID = 52

    /**
     * Call on every status check: shows the reminder while [adminActive] is false.
     * Pass true when the parent has uninstall protection switched off.
     */
    fun update(context: Context, adminActive: Boolean) {
        val nm = NotificationManagerCompat.from(context)
        if (adminActive) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Protection setup", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Shows while Shield's uninstall protection is off." },
            )
        }
        val tap = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(PermissionNavigator.EXTRA_PERMISSION, "admin"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = "Uninstall protection is off. Tap to turn it on."
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Finish Shield setup")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tap)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) }
    }
}
