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
import com.shahaabapps.core.model.TrailPoint
import com.shahaabapps.core.model.TravelStop
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
    @Inject lateinit var childRepository: com.shahaabapps.core.firebase.child.ChildRepository
    @Inject lateinit var registrar: GeofenceRegistrar
    @Inject lateinit var ringController: RingController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var locationCallback: LocationCallback? = null
    private var started = false
    private var lastRingNonce: String? = null

    // In-memory "stop" cluster for travel history.
    private var stopAnchor: android.location.Location? = null
    private var stopArrivalMillis = 0L
    private var stopLastMillis = 0L

    // Jitter filtering: the last accepted (trusted) position, a smoothing window
    // for a steady marker while stationary, and a tentative move awaiting confirmation.
    private var accepted: android.location.Location? = null
    private var acceptedTime = 0L
    private val smoothWindow = ArrayDeque<android.location.Location>()
    private var tentativeMove: android.location.Location? = null

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
        // Presence heartbeat: keep lastSeen fresh so the parent shows "Online",
        // and report the current battery level.
        scope.launch {
            while (true) {
                runCatching { childRepository.heartbeat(childId, android.os.Build.MODEL, batteryPercent()) }
                kotlinx.coroutines.delay(HEARTBEAT_MS)
            }
        }
        // Stop everything if the device is unlinked (no longer supervised).
        scope.launch {
            childRepository.observeChild(childId).collectLatest { child ->
                if (child != null && !child.isLinked) {
                    runCatching { registrar.register(emptyList()) }
                    stopSelf()
                }
            }
        }
        // Parent pull-to-refresh: publish a fresh high-accuracy fix on demand.
        scope.launch {
            var lastLocationNonce = 0L
            var firstEmit = true
            deviceRepository.observeLocationRequests(childId).collectLatest { nonce ->
                val recent = System.currentTimeMillis() - nonce < FRESH_REQUEST_MS
                if (firstEmit) {
                    firstEmit = false
                    lastLocationNonce = nonce
                    return@collectLatest
                }
                if (nonce > lastLocationNonce && recent) {
                    lastLocationNonce = nonce
                    publishFreshFix(childId)
                }
            }
        }
        startLocationUpdates(childId)
        // Register the periodic backstop so location keeps flowing even if this
        // foreground service is later killed by the OS.
        runCatching { LocationSyncWorker.schedule(this) }
    }

    @SuppressLint("MissingPermission")
    private fun publishFreshFix(childId: String) {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return
        runCatching {
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc -> loc?.let { publishLocation(childId, it) } }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(childId: String) {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return

        // Reset the jitter filter so the first fresh fix is trusted (no line drawn
        // from a stale position left over from a previous run).
        accepted = null
        acceptedTime = 0L
        tentativeMove = null
        smoothWindow.clear()

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
            // Only report when the child actually moves — saves battery and
            // Firestore writes while they're stationary (home, school, etc.).
            .setMinUpdateDistanceMeters(MOVE_DISTANCE_METERS)
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

    /**
     * Filters out GPS jitter/drift so a stationary device (e.g. sleeping) doesn't
     * fake movement: drops low-accuracy and teleport fixes, holds the marker steady
     * (smoothed centroid) while within the accuracy noise, and only records real
     * movement to the trail after a one-fix confirmation.
     */
    private fun publishLocation(childId: String, loc: android.location.Location) {
        val now = System.currentTimeMillis()
        val prev = accepted

        // First fix ever (or after a restart): trust it.
        if (prev == null) {
            commitAccepted(childId, loc, now)
            return
        }
        // 1. Accuracy gate — ignore poor fixes. Relax it while the device is moving
        //    (travelling): a stricter gate drops valid highway fixes, leaving holes.
        val moving = loc.hasSpeed() && loc.speed > MOVING_SPEED_MPS
        val maxAccuracy = if (moving) ACCURACY_MAX_MOVING_METERS else ACCURACY_MAX_METERS
        if (loc.accuracy > maxAccuracy) return
        // 2. Teleport gate — ignore impossibly fast jumps.
        val dtSec = ((now - acceptedTime).coerceAtLeast(1L)) / 1000.0
        val dist = prev.distanceTo(loc)
        if (dist / dtSec > MAX_SPEED_MPS) return
        // 3. Jitter gate — within combined accuracy noise = still stationary.
        val noise = maxOf(prev.accuracy, loc.accuracy) + MOVE_MARGIN_METERS
        if (dist <= noise) {
            tentativeMove = null
            smoothWindow.addLast(loc)
            while (smoothWindow.size > SMOOTH_WINDOW) smoothWindow.removeFirst()
            val cLat = smoothWindow.map { it.latitude }.average()
            val cLng = smoothWindow.map { it.longitude }.average()
            publishLive(childId, cLat, cLng, loc.accuracy, now)
            return
        }
        // 4. Movement candidate — confirm with one more consistent fix before
        //    treating it as real (so a single bad fix can't start a fake trip).
        val tent = tentativeMove
        if (tent == null) {
            tentativeMove = loc
            publishLive(childId, loc.latitude, loc.longitude, loc.accuracy, now)
            return
        }
        if (tent.distanceTo(loc) <= noise || prev.distanceTo(loc) > noise) {
            commitAccepted(childId, loc, now) // confirmed movement
        } else {
            tentativeMove = null // bounced back — it was a blip
            publishLive(childId, prev.latitude, prev.longitude, prev.accuracy, now)
        }
    }

    /** Accepts [loc] as the trusted position: updates live, trail and stop tracking. */
    private fun commitAccepted(childId: String, loc: android.location.Location, now: Long) {
        accepted = loc
        acceptedTime = now
        tentativeMove = null
        smoothWindow.clear()
        smoothWindow.addLast(loc)
        publishLive(childId, loc.latitude, loc.longitude, loc.accuracy, now)
        scope.launch {
            runCatching {
                deviceRepository.appendTrailPoint(
                    childId,
                    today(now),
                    TrailPoint(latitude = loc.latitude, longitude = loc.longitude, timestampMillis = now),
                )
            }
        }
        trackStop(childId, loc, now)
    }

    private fun publishLive(childId: String, lat: Double, lng: Double, acc: Float, now: Long) {
        scope.launch {
            runCatching {
                deviceRepository.updateLocation(
                    childId,
                    ChildLocation(latitude = lat, longitude = lng, accuracyMeters = acc, timestampMillis = now),
                )
            }
        }
    }

    /** Current battery level 0-100, or 0 if unavailable. */
    private fun batteryPercent(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager ?: return 0
        val pct = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (pct in 1..100) pct else 0
    }

    private fun today(millis: Long): String =
        java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
            .toLocalDate().toString()

    /**
     * Groups nearby fixes into a "stop". When the child leaves a spot they stayed
     * at for at least [STOP_MIN_MS], the completed stop is saved to travel history.
     */
    private fun trackStop(childId: String, loc: android.location.Location, now: Long) {
        val anchor = stopAnchor
        if (anchor == null) {
            stopAnchor = loc
            stopArrivalMillis = now
            stopLastMillis = now
            return
        }
        if (anchor.distanceTo(loc) <= STOP_RADIUS_METERS) {
            // Still at the same place — extend the stay.
            stopLastMillis = now
            return
        }
        // Moved away: record the previous stay if it was long enough. Use `now`
        // for departure because, with move-only updates, no fixes arrive while
        // stationary — so the stay lasts from arrival until this leaving fix.
        if (now - stopArrivalMillis >= STOP_MIN_MS) {
            val stop = TravelStop(
                latitude = anchor.latitude,
                longitude = anchor.longitude,
                arrivalMillis = stopArrivalMillis,
                departureMillis = now,
            )
            scope.launch { runCatching { deviceRepository.addTravelStop(childId, stop) } }
        }
        stopAnchor = loc
        stopArrivalMillis = now
        stopLastMillis = now
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

        // Only honour a fresh-location request this recent (avoids replay on restart).
        private const val FRESH_REQUEST_MS = 2 * 60_000L

        // Travel-history "stop" detection.
        private const val STOP_RADIUS_METERS = 150f
        private const val STOP_MIN_MS = 5 * 60_000L // 5 minutes

        // Only report location after moving this far — the move-only optimization.
        private const val MOVE_DISTANCE_METERS = 40f

        // Jitter filtering (Phase 1 + 2).
        private const val ACCURACY_MAX_METERS = 50f   // drop fixes worse than this (stationary)
        private const val ACCURACY_MAX_MOVING_METERS = 120f // relaxed while travelling
        private const val MOVING_SPEED_MPS = 2.5f     // ~9 km/h: clearly moving
        private const val MOVE_MARGIN_METERS = 15f    // movement must beat the accuracy noise by this
        private const val MAX_SPEED_MPS = 60f         // ~216 km/h teleport ceiling
        private const val SMOOTH_WINDOW = 5           // fixes averaged for a steady marker

        // Presence heartbeat interval.
        private const val HEARTBEAT_MS = 2 * 60_000L

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
