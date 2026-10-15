package com.shahaabapps.shield.enforcement

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shahaabapps.core.model.DevicePermissions

/** Reads the device's current special-access grants into a [DevicePermissions]. */
object ChildPermissions {

    fun snapshot(context: Context): DevicePermissions {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            fine || coarse // pre-Android 10 had no separate background grant
        }
        val battery = runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)
        val notifications = runCatching {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }.getOrDefault(false)

        return DevicePermissions(
            location = fine || coarse,
            locationAlways = background,
            accessibility = EnforcementPermissions.isAccessibilityEnabled(context),
            usageAccess = EnforcementPermissions.hasUsageAccess(context),
            notifications = notifications,
            batteryUnrestricted = battery,
            reportedAtMillis = System.currentTimeMillis(),
        )
    }
}
