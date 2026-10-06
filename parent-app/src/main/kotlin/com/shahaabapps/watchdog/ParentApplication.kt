package com.shahaabapps.watchdog

import android.app.Application
import com.shahaabapps.watchdog.messaging.WatchdogMessagingService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ParentApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Create the geofence-alerts channel up front so background push
        // notifications (which reference it) are shown on Android O+.
        WatchdogMessagingService.ensureChannel(this)
    }
}
