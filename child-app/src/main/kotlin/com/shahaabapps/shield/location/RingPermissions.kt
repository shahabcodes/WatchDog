package com.shahaabapps.shield.location

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Permissions that let Shield alert the child: showing notifications
 * (POST_NOTIFICATIONS, Android 13+) and popping the full-screen ring alarm over
 * the lock screen (USE_FULL_SCREEN_INTENT, Android 14+).
 */
object RingPermissions {

    /** Whether POST_NOTIFICATIONS is granted (always true below Android 13). */
    fun hasNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Only Android 13+ has a runtime notification permission to request. */
    fun notificationsRequestable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Whether the full-screen ring alarm can launch itself. Below Android 14 this
     * is always allowed; on 14+ it depends on the special "Full-screen
     * notifications" app setting.
     */
    fun canUseFullScreen(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return true
        return manager.canUseFullScreenIntent()
    }

    /** Only Android 14+ exposes a settings screen to grant full-screen intents. */
    fun fullScreenRequestable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    fun fullScreenSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Uri.fromParts("package", context.packageName, null),
        )

    fun appNotificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
}
