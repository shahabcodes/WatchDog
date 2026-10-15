package com.shahaabapps.shield

import android.app.Application
import com.shahaabapps.shield.location.LocationFlushWorker
import com.shahaabapps.shield.location.LocationSyncWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ChildApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Keep the location backstop + flush scheduled on every process start,
        // independent of the foreground service (which OEM battery managers may
        // kill). These publish a fix + heartbeat even when the service is dead.
        runCatching { LocationSyncWorker.schedule(this) }
        runCatching { LocationFlushWorker.schedulePeriodic(this) }
    }
}
