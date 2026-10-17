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
import com.shahaabapps.core.model.Geofence
import com.shahaabapps.core.model.GeofenceEvent
import com.shahaabapps.core.model.GeofenceShape
import com.shahaabapps.core.model.GeofenceTransition
import com.shahaabapps.core.model.GeofenceTrigger
import com.shahaabapps.core.model.GeoMath
import com.shahaabapps.core.model.TrailPoint
import com.shahaabapps.core.model.TravelStop
import com.shahaabapps.shield.data.PendingTrailDao
import com.shahaabapps.shield.data.PendingTrailPoint
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
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
    @Inject lateinit var pendingTrailDao: PendingTrailDao
    @Inject lateinit var trackingRepository: com.shahaabapps.core.firebase.tracking.TrackingRepository
    @Inject lateinit var e2eeManager: com.shahaabapps.core.firebase.e2ee.E2eeManager
    @Inject lateinit var installGuardRepository: com.shahaabapps.core.firebase.installguard.InstallGuardRepository

    private var apkInstallGuard: com.shahaabapps.shield.install.ApkInstallGuard? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var locationCallback: LocationCallback? = null
    private var started = false
    private var lastRingNonce: String? = null

    // Parent-chosen tracking accuracy + adaptive-sampling state.
    @Volatile
    private var trackingMode: com.shahaabapps.core.model.TrackingMode = com.shahaabapps.core.model.TrackingMode.DEFAULT
    @Volatile
    private var denseActive = false
    @Volatile
    private var lastMovementMillis = 0L

    // Active geofences + pending-arrival dwell timers, for stream-based
    // enter/exit detection (a reliable backup to Play Services geofencing).
    @Volatile
    private var activeGeofences: List<Geofence> = emptyList()
    private val dwellSince = java.util.concurrent.ConcurrentHashMap<String, Long>()

    // Battery sampling for the drain-rate estimate reported to the parent.
    private var lastBatteryPct = -1
    private var lastBatteryTime = 0L
    private var lastBatteryCounterUah = -1 // fine-grained charge (µAh), -1 if unavailable
    private var drainPerHour = 0.0
    private val batteryTracker by lazy { BatteryStatsTracker(applicationContext) }

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
        // Watch downloaded APKs and quarantine ones the parent's sideload policy blocks.
        apkInstallGuard = com.shahaabapps.shield.install.ApkInstallGuard(
            this, scope, installGuardRepository, authRepository,
        ).also { it.start() }
        // Establish E2EE readiness in this (possibly Activity-less) process so that
        // location/trail writes here are encrypted once every parent is wrapped.
        scope.launch {
            // Run on each snapshot (not debounced) so a parent that publishes its
            // key after being linked is wrapped on the next heartbeat; E2eeManager
            // keeps repeat calls near-free.
            childRepository.observeChild(childId)
                .map { (it?.linkedParents ?: emptyList()) + (it?.viewers ?: emptyList()) }
                .collect { parents -> runCatching { e2eeManager.syncChildKeys(childId, parents) } }
        }
        scope.launch {
            geofenceRepository.observeGeofences(childId).collectLatest { geofences ->
                activeGeofences = geofences
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
        // and report battery level, charging state and recent drain rate.
        scope.launch {
            while (true) {
                val pct = batteryPercent()
                val charging = isPlugged()
                val now = System.currentTimeMillis()
                // Estimate discharge rate over a window of at least DRAIN_WINDOW_HOURS.
                // Measuring over the ~2-min heartbeat makes a single 1% tick look like
                // ~30%/hr; a ≥15-min reference keeps it realistic.
                when {
                    charging -> {
                        drainPerHour = 0.0
                        lastBatteryPct = -1
                        lastBatteryTime = 0L
                        lastBatteryCounterUah = -1
                    }
                    pct in 1..100 -> {
                        val counterNow = chargeCounterUah()
                        if (lastBatteryPct !in 1..100 || lastBatteryTime <= 0L) {
                            lastBatteryPct = pct
                            lastBatteryTime = now
                            lastBatteryCounterUah = counterNow
                        } else {
                            val dtHours = (now - lastBatteryTime) / 3_600_000.0
                            if (dtHours >= DRAIN_WINDOW_HOURS) {
                                val rate = when {
                                    // Precise: fine-grained charge-counter delta (µAh), scaled by
                                    // the estimated full capacity — avoids 1% quantization.
                                    lastBatteryCounterUah > 0 && counterNow in 1 until lastBatteryCounterUah -> {
                                        val fullStart = lastBatteryCounterUah / (lastBatteryPct / 100.0)
                                        val fullNow = counterNow / (pct / 100.0)
                                        val fullCap = (fullStart + fullNow) / 2.0
                                        if (fullCap > 0) {
                                            (lastBatteryCounterUah - counterNow) / fullCap * 100.0 / dtHours
                                        } else {
                                            0.0
                                        }
                                    }
                                    // Fallback: integer %-per-hour over the window.
                                    pct < lastBatteryPct -> (lastBatteryPct - pct) / dtHours
                                    else -> 0.0
                                }.coerceIn(0.0, 99.0)
                                if (rate > 0.0) {
                                    drainPerHour = if (drainPerHour <= 0.0) rate else drainPerHour * 0.5 + rate * 0.5
                                }
                                // Restart the window from the current reading either way.
                                lastBatteryPct = pct
                                lastBatteryTime = now
                                lastBatteryCounterUah = counterNow
                            }
                        }
                    }
                }
                // Record battery stats locally (persisted) every cycle — this must
                // run even when offline, so charge sessions and hourly samples are
                // captured and then uploaded on reconnect.
                val stats = runCatching { batteryTracker.record(pct, charging, now, batteryTempTenths()) }.getOrNull()
                val perms = com.shahaabapps.shield.enforcement.ChildPermissions.snapshot(this@GeofenceSyncService)
                val drain = drainPerHour
                // Fire-and-forget the network writes so the sampling loop keeps its
                // 2-min cadence even offline (the writes queue in Firestore's offline
                // cache and sync on reconnect) instead of blocking on await().
                scope.launch {
                    runCatching { childRepository.heartbeat(childId, android.os.Build.MODEL, pct, charging, drain) }
                    runCatching { childRepository.updatePermissions(childId, perms) }
                    if (stats != null) runCatching { childRepository.updateBatteryStats(childId, stats) }
                }
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
        // Parent-chosen tracking accuracy — re-tune sampling when it changes.
        scope.launch {
            trackingRepository.observe(childId).collectLatest { mode ->
                trackingMode = mode
                applyLocationRequest(desiredDense(System.currentTimeMillis()))
            }
        }
        startLocationUpdates(childId)
        // Register the periodic backstop so location keeps flowing even if this
        // foreground service is later killed by the OS.
        runCatching { LocationSyncWorker.schedule(this) }
        // Backstop that uploads any buffered breadcrumbs when the network returns.
        runCatching { LocationFlushWorker.schedulePeriodic(this) }
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

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val now = System.currentTimeMillis()
                // Movement detection drives adaptive accuracy: speed, or a jump
                // beyond the move gate from the last trusted position.
                val moving = (loc.hasSpeed() && loc.speed > MOVING_SPEED_MPS) ||
                    (accepted?.let { it.distanceTo(loc) > MOVE_DISTANCE_METERS } ?: false)
                if (moving) lastMovementMillis = now
                val wantDense = desiredDense(now)
                if (wantDense != denseActive) applyLocationRequest(wantDense)
                publishLocation(childId, loc)
            }
        }
        locationCallback = callback
        applyLocationRequest(desiredDense(System.currentTimeMillis()))
    }

    /** Whether dense/high-accuracy sampling should be active right now. */
    private fun desiredDense(now: Long): Boolean = when (trackingMode) {
        com.shahaabapps.core.model.TrackingMode.SAVER -> false
        com.shahaabapps.core.model.TrackingMode.HIGH -> true
        // Adaptive: dense while moving (and briefly after), light when settled.
        com.shahaabapps.core.model.TrackingMode.ADAPTIVE -> (now - lastMovementMillis) < STILL_TIMEOUT_MS
    }

    private fun buildLocationRequest(dense: Boolean): LocationRequest =
        if (dense) {
            // High accuracy, frequent, no move gate → the trail hugs the route.
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, DENSE_INTERVAL_MS)
                .setMinUpdateIntervalMillis(DENSE_FASTEST_MS)
                .setMinUpdateDistanceMeters(0f)
                .build()
        } else {
            // Balanced + move-gated → light on battery while stationary.
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, LOCATION_INTERVAL_MS)
                .setMinUpdateIntervalMillis(LOCATION_FASTEST_MS)
                .setMinUpdateDistanceMeters(MOVE_DISTANCE_METERS)
                .build()
        }

    @SuppressLint("MissingPermission")
    private fun applyLocationRequest(dense: Boolean) {
        val cb = locationCallback ?: return
        denseActive = dense
        runCatching { fusedClient.removeLocationUpdates(cb) }
        runCatching { fusedClient.requestLocationUpdates(buildLocationRequest(dense), cb, Looper.getMainLooper()) }
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
        //    Hold the live marker at the last confirmed position until then, so an
        //    unconfirmed Wi-Fi/cell fix can't teleport it to a nearby place.
        val tent = tentativeMove
        if (tent == null) {
            tentativeMove = loc
            publishLive(childId, prev.latitude, prev.longitude, prev.accuracy, now)
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
        val point = TrailPoint(latitude = loc.latitude, longitude = loc.longitude, timestampMillis = now)
        scope.launch {
            // Primary: write the breadcrumb straight to Firestore. Online it lands
            // immediately; offline, Firestore's own on-disk queue holds it and syncs
            // on reconnect. (arrayUnion dedupes, so the Room backup below can't dupe.)
            runCatching { deviceRepository.appendTrailPoint(childId, today(now), point) }
            // Backup: also buffer durably on-device and let the flush worker upload
            // it, in case the direct write is ever lost (app killed mid-sync, etc.).
            runCatching {
                pendingTrailDao.insert(
                    PendingTrailPoint(childId = childId, lat = loc.latitude, lng = loc.longitude, timestampMillis = now),
                )
            }
            LocationFlushWorker.flushNow(applicationContext)
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
        detectGeofenceTransitions(childId, lat, lng, acc, now)
    }

    /**
     * Enter/exit detection from the continuous location stream — a reliable
     * backstop to Play Services geofencing (which often drops transitions). Shares
     * [GeofenceStateStore] with [GeofenceBroadcastReceiver], so whichever observes
     * a transition first flips the state and the other won't double-report.
     *
     * Arrival requires dwelling [DWELL_DELAY_MS] inside (a pass-by doesn't count);
     * departure requires moving beyond the radius + [EXIT_BUFFER_METERS] after a
     * recorded arrival (hysteresis avoids boundary flip-flop).
     */
    @Synchronized
    private fun detectGeofenceTransitions(childId: String, lat: Double, lng: Double, acc: Float, now: Long) {
        if (acc > 0f && acc > GEO_ACCURACY_MAX_METERS) return
        for (g in activeGeofences) {
            if (!g.active || g.id.isBlank()) continue
            val wantsEnter = g.trigger == GeofenceTrigger.ENTER || g.trigger == GeofenceTrigger.ENTER_EXIT
            val wantsExit = g.trigger == GeofenceTrigger.EXIT || g.trigger == GeofenceTrigger.ENTER_EXIT
            val distToCenter = GeoMath.distanceMeters(lat, lng, g.latitude, g.longitude)
            val inside = if (g.shape == GeofenceShape.POLYGON && g.points.size >= 3) {
                GeoMath.containsPoint(g.points, lat, lng)
            } else {
                distToCenter <= g.radiusMeters
            }
            val beyondExit = distToCenter > g.radiusMeters + EXIT_BUFFER_METERS
            val wasInside = GeofenceStateStore.isInside(this, g.id)

            when {
                inside && !wasInside -> {
                    val since = dwellSince.getOrPut(g.id) { now }
                    if (now - since >= DWELL_DELAY_MS) {
                        dwellSince.remove(g.id)
                        GeofenceStateStore.setInside(this, g.id, true)
                        if (wantsEnter) reportTransition(childId, g, GeofenceTransition.ENTER, lat, lng, now)
                    }
                }
                inside -> dwellSince.remove(g.id) // already inside / counted
                else -> {
                    dwellSince.remove(g.id) // left before dwelling → cancel pending arrival
                    if (wasInside && beyondExit) {
                        GeofenceStateStore.setInside(this, g.id, false)
                        if (wantsExit) reportTransition(childId, g, GeofenceTransition.EXIT, lat, lng, now)
                    }
                }
            }
        }
    }

    private fun reportTransition(childId: String, g: Geofence, transition: GeofenceTransition, lat: Double, lng: Double, now: Long) {
        scope.launch {
            runCatching {
                geofenceRepository.reportEvent(
                    childId,
                    GeofenceEvent(
                        geofenceId = g.id,
                        geofenceName = g.name,
                        transition = transition,
                        latitude = lat,
                        longitude = lng,
                        timestampMillis = now,
                    ),
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

    /**
     * Whether the device is plugged into power. Uses the sticky battery intent's
     * EXTRA_PLUGGED, which stays true at 100%/Full (unlike isCharging(), which can
     * read false when the battery is full while still plugged in — that would
     * falsely look like an unplug and end the charge session early).
     */
    private fun isPlugged(): Boolean = runCatching {
        val intent = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        (intent?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }.getOrDefault(false)

    /** Battery temperature in tenths of °C from the sticky battery intent; 0 if unknown. */
    private fun batteryTempTenths(): Int = runCatching {
        val intent = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val t = intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        if (t in 1..1000) t else 0 // plausible 0.1–100.0 °C
    }.getOrDefault(0)

    /** Remaining charge in µAh (fine-grained), or -1 when the device doesn't report it. */
    private fun chargeCounterUah(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager ?: return -1
        val v = runCatching {
            bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        }.getOrDefault(Int.MIN_VALUE)
        return if (v > 0) v else -1 // Integer.MIN_VALUE / 0 means unsupported
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
        apkInstallGuard?.stop()
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
        private const val STOP_MIN_MS = 2 * 60_000L // 2 minutes (shorter stays count)

        // Only report location after moving this far — the move-only optimization.
        private const val MOVE_DISTANCE_METERS = 40f

        // Jitter filtering (Phase 1 + 2).
        private const val ACCURACY_MAX_METERS = 50f   // drop fixes worse than this (stationary)
        private const val ACCURACY_MAX_MOVING_METERS = 120f // relaxed while travelling
        private const val GEO_ACCURACY_MAX_METERS = 120f // max fix fuzziness for geofence enter/exit
        private const val DRAIN_WINDOW_HOURS = 0.25 // ≥15 min window for a stable drain rate
        // Adaptive high-accuracy sampling while moving (dense), vs balanced when still.
        private const val DENSE_INTERVAL_MS = 8_000L
        private const val DENSE_FASTEST_MS = 4_000L
        private const val STILL_TIMEOUT_MS = 2 * 60_000L // drop to light after this with no movement
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
