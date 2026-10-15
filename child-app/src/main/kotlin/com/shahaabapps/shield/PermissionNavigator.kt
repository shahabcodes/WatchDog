package com.shahaabapps.shield

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Maps a requested permission to the exact settings screen that grants it. */
object PermissionNavigator {

    const val EXTRA_PERMISSION = "permission"

    fun intentFor(context: Context, permission: String): Intent {
        val pkg = context.packageName
        return when (permission) {
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "usageAccess" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            "notifications" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            "battery" -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg"))
            "fullScreenAlarm" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg"))
            } else {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            }
            // location (incl. "Allow all the time") and any fallback → app details,
            // where the user can set Location permission / all-the-time.
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun label(permission: String): String = when (permission) {
        "location" -> "Location — set to \"Allow all the time\""
        "accessibility" -> "Accessibility for Shield"
        "usageAccess" -> "Usage access for Shield"
        "notifications" -> "Notifications for Shield"
        "battery" -> "Unrestricted battery for Shield"
        "fullScreenAlarm" -> "Full-screen alarm for Shield"
        else -> "the requested permission"
    }
}
