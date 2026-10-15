package com.shahaabapps.shield.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest

/** Restarts geofence syncing after the device reboots. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val hasFineLocation = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (hasFineLocation) {
            GeofenceSyncService.start(context)
        }
        // Also (re)schedule the backstop workers: on Android 12+ the foreground
        // service often can't auto-start from boot, so these keep location +
        // heartbeat flowing until the app is next opened.
        runCatching { LocationSyncWorker.schedule(context) }
        runCatching { LocationFlushWorker.schedulePeriodic(context) }
    }
}
