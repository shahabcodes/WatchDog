package com.shahaabapps.shield.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.device.DeviceRepository
import com.shahaabapps.core.model.ChildLocation
import com.shahaabapps.core.model.TrailPoint
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * A periodic safety net: even if the foreground [GeofenceSyncService] is killed by
 * the OS during a long trip, this worker still publishes a location fix every ~15
 * minutes (the WorkManager minimum). Writes queue offline and upload on reconnect.
 */
class LocationSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun deviceRepository(): DeviceRepository
        fun authRepository(): AuthRepository
        fun childRepository(): com.shahaabapps.core.firebase.child.ChildRepository
        fun pendingTrailDao(): com.shahaabapps.shield.data.PendingTrailDao
    }

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val childId = deps.authRepository().currentUserIdOrNull() ?: return Result.success()

        // Heartbeat first, so the parent's "last seen" + battery stay fresh even
        // when the foreground service is dead and even if the location fix fails.
        runCatching {
            val bm = applicationContext.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val pct = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 1..100 } ?: 0
            val batteryIntent = runCatching {
                applicationContext.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            }.getOrNull()
            val plugged = (batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            val tempTenths = (batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0).takeIf { it in 1..1000 } ?: 0
            deps.childRepository().heartbeat(childId, android.os.Build.MODEL, pct, plugged, 0.0, tempTenths)
            // Keep battery history + charge-session tracking going even if the
            // foreground service is dead (shares the same persisted state).
            val stats = BatteryStatsTracker(applicationContext).record(pct, plugged, System.currentTimeMillis(), tempTenths)
            deps.childRepository().updateBatteryStats(childId, stats)
        }

        val fine = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }

        val fused = LocationServices.getFusedLocationProviderClient(applicationContext)
        val loc = awaitCurrentLocation(fused) ?: return Result.success()

        val now = System.currentTimeMillis()
        val date = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        // Live + breadcrumb written directly (Firestore queues offline). Also buffer
        // the breadcrumb to the durable Room queue as a backup (arrayUnion dedupes).
        runCatching {
            deps.deviceRepository().updateLocation(
                childId,
                ChildLocation(loc.latitude, loc.longitude, loc.accuracy, now),
            )
        }
        runCatching {
            deps.deviceRepository().appendTrailPoint(
                childId,
                date,
                TrailPoint(latitude = loc.latitude, longitude = loc.longitude, timestampMillis = now),
            )
        }
        runCatching {
            deps.pendingTrailDao().insert(
                com.shahaabapps.shield.data.PendingTrailPoint(
                    childId = childId,
                    lat = loc.latitude,
                    lng = loc.longitude,
                    timestampMillis = now,
                ),
            )
        }
        LocationFlushWorker.flushNow(applicationContext)
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitCurrentLocation(
        fused: com.google.android.gms.location.FusedLocationProviderClient,
    ): android.location.Location? = suspendCancellableCoroutine { cont ->
        runCatching {
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    companion object {
        private const val NAME = "location-backstop"

        /** Registers the periodic backstop (keeps any existing schedule). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LocationSyncWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
