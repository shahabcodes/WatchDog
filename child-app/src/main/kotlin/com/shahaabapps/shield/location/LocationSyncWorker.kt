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
    }

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val fine = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val childId = deps.authRepository().currentUserIdOrNull() ?: return Result.success()

        val fused = LocationServices.getFusedLocationProviderClient(applicationContext)
        val loc = awaitCurrentLocation(fused) ?: return Result.success()

        val now = System.currentTimeMillis()
        runCatching {
            deps.deviceRepository().updateLocation(
                childId,
                ChildLocation(loc.latitude, loc.longitude, loc.accuracy, now),
            )
            deps.deviceRepository().appendTrailPoint(
                childId,
                Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                TrailPoint(latitude = loc.latitude, longitude = loc.longitude, timestampMillis = now),
            )
        }
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
