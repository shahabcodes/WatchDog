package com.shahaabapps.shield.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.device.DeviceRepository
import com.shahaabapps.core.firebase.geofence.GeofenceRepository
import com.shahaabapps.core.model.ChildLocation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps the child's geofences registered, publishes the
 * device's live location to the parent, and plays the "ring device" alarm.
 */
@AndroidEntryPoint
class GeofenceSyncService : Service() {

    @Inject lateinit var geofenceRepository: GeofenceRepository
    @Inject lateinit var deviceRepository: DeviceRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var registrar: GeofenceRegistrar
    @Inject lateinit var ringController: RingController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var locationCallback: LocationCallback? = null
    private var started = false
    private var lastRingNonce: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForegroundCompat()
        } catch (t: Throwable) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_RING_SNOOZE -> ringController.snooze()
            ACTION_RING_RESUME -> ringController.start(ringController.lastMessage())
            ACTION_RING_DISMISS -> {
                ringController.stop()
                val childId = authRepository.currentUserIdOrNull()
                if (childId != null) scope.launch { runCatching { deviceRepository.clearRing(childId) } }
            }
            else -> startSyncing()
        }
        return START_STICKY
    }

    private fun startSyncing() {
        if (started) return
        started = true
        val childId = authRepository.currentUserIdOrNull() ?: return
        scope.launch {
            geofenceRepository.observeGeofences(childId).collectLatest { geofences ->
                runCatching { registrar.register(geofences) }
            }
        }
        scope.launch {
            deviceRepository.observeRing(childId).collectLatest { ring ->
                when {
                    ring == null || !ring.active -> ringController.stop()
                    ring.nonce != lastRingNonce -> {
                        lastRingNonce = ring.nonce
                        ringController.start(ring.message)
                    }
                }
            }
        }
        startLocationUpdates(childId)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(childId: String) {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return

        // Publish the last-known location right away so the parent sees something
        // immediately (location callbacks only fire on a *new* computed fix).
        runCatching {
            fusedClient.lastLocation.addOnSuccessListener { loc -> loc?.let { publishLocation(childId, it) } }
        }
        // Also actively request a fresh fix once, in case there is no cached one.
        runCatching {
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc -> loc?.let { publishLocation(childId, it) } }
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LOCATION_FASTEST_MS)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                publishLocation(childId, loc)
            }
        }
        locationCallback = callback
        runCatching { fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper()) }
    }

    private fun publishLocation(childId: String, loc: android.location.Location) {
        scope.launch {
            runCatching {
                deviceRepository.updateLocation(
                    childId,
                    ChildLocation(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracyMeters = loc.accuracy,
                        timestampMillis = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    private fun startForegroundCompat() {
        val channelId = "geofence_sync"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(channelId) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(channelId, "Location protection", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("Shield is active")
            .setContentText("Keeping your family's places up to date")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { runCatching { fusedClient.removeLocationUpdates(it) } }
        scope.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 42
        private const val LOCATION_INTERVAL_MS = 60_000L
        private const val LOCATION_FASTEST_MS = 30_000L

        const val ACTION_RING_SNOOZE = "com.shahaabapps.shield.action.RING_SNOOZE"
        const val ACTION_RING_DISMISS = "com.shahaabapps.shield.action.RING_DISMISS"
        const val ACTION_RING_RESUME = "com.shahaabapps.shield.action.RING_RESUME"

        fun start(context: Context) {
            val intent = Intent(context, GeofenceSyncService::class.java)
            // Starting a foreground service can throw (e.g. background-start
            // restrictions on Android 12+); never let that crash the app.
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }
}
