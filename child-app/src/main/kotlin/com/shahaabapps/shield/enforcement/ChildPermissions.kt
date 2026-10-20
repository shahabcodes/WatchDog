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
        // Full-screen intent is manifest-granted below Android 14; user-toggleable on 14+.
        val fullScreenAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
                    .canUseFullScreenIntent()
            }.getOrDefault(false)
        } else {
            true
        }
        val storage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { android.os.Environment.isExternalStorageManager() }.getOrDefault(false)
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

        // Device state that stops background location even when permissions are fine.
        val powerSave = runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
        }.getOrDefault(false)
        val locationOff = runCatching {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) !lm.isLocationEnabled
            else !(lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER))
        }.getOrDefault(false)
        val bgRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
                    .isBackgroundRestricted
            }.getOrDefault(false)
        } else {
            false
        }

        return DevicePermissions(
            location = fine || coarse,
            locationAlways = background,
            accessibility = EnforcementPermissions.isAccessibilityEnabled(context),
            usageAccess = EnforcementPermissions.hasUsageAccess(context),
            adminActive = runCatching { DeviceAdmin.isActive(context) }.getOrDefault(false),
            clockOk = !TrustedClock.clockLooksChanged(),
            gpsGenuine = com.shahaabapps.shield.location.MockLocationGuard.gpsGenuine(context),
            parentKeysOk = context.getSharedPreferences(
                com.shahaabapps.core.firebase.e2ee.E2eeManager.PARENT_PINS_PREFS,
                android.content.Context.MODE_PRIVATE,
            ).all.keys.none { it.startsWith(com.shahaabapps.core.firebase.e2ee.E2eeManager.PENDING_PREFIX) },
            phoneZone = java.time.ZoneId.systemDefault().id,
            homeZone = ChildZone.zone().id,
            notifications = notifications,
            batteryUnrestricted = battery,
            fullScreenAlarm = fullScreenAlarm,
            storageAccess = storage,
            powerSaveMode = powerSave,
            locationServicesOff = locationOff,
            backgroundRestricted = bgRestricted,
            reportedAtMillis = System.currentTimeMillis(),
        )
    }
}
