package com.shahaabapps.shield.location

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import com.google.android.gms.location.Geofence as GmsGeofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.geofence.GeofenceRepository
import com.shahaabapps.core.model.GeoMath
import com.shahaabapps.core.model.GeofenceEvent
import com.shahaabapps.core.model.GeofenceTransition
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Receives geofence transitions and reports them to Firestore for the parent. */
@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var geofenceRepository: GeofenceRepository
    @Inject lateinit var authRepository: AuthRepository

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        val gmsTransition = event.geofenceTransition
        val childId = authRepository.currentUserIdOrNull() ?: return
        val triggering = event.triggeringGeofences ?: return
        val lat = event.triggeringLocation?.latitude ?: 0.0
        val lng = event.triggeringLocation?.longitude ?: 0.0
        val now = System.currentTimeMillis()

        // Accuracy gate: near a small place, a fuzzy fix can appear inside/outside
        // when it isn't. Ignore transitions from untrustworthy fixes. Departures get
        // a stricter gate — a phantom overnight "left" from a drifted low-power fix is
        // the usual cause of repeat home visits, so an EXIT must come from a confident fix.
        val triggerLoc = event.triggeringLocation
        // Transitions from a fake (mock) location are ignored.
        if (triggerLoc != null && MockLocationGuard.reject(context, triggerLoc)) return
        val acc = triggerLoc?.takeIf { it.hasAccuracy() }?.accuracy
        if (acc != null && acc > ACCURACY_MAX_METERS) return
        if (gmsTransition == GmsGeofence.GEOFENCE_TRANSITION_EXIT && acc != null && acc > EXIT_ACCURACY_MAX_METERS) return

        // Map Play Services transitions to our model, honouring the inner/outer
        // geofence design: DWELL on the inner fence = "arrived", EXIT on the
        // outer (radius + buffer) fence = "left". Raw ENTER is ignored.
        //
        // A "left" alert only fires if we previously recorded an "arrived" for the
        // same place — so merely passing within the outer fence (and leaving it)
        // without ever dwelling inside no longer reports a spurious departure.
        // Arrivals (inner DWELL) are reported immediately. Departures (outer EXIT)
        // are collected as CANDIDATES and confirmed with a fresh fix below — the OS
        // can fire a phantom EXIT from a bad fix (reporting "left" while actually
        // inside), so we don't flip state or report until we've verified it.
        val enters = ArrayList<String>()
        val exitCandidates = ArrayList<String>()
        triggering.forEach { gf ->
            val reqId = gf.requestId
            when {
                gmsTransition == GmsGeofence.GEOFENCE_TRANSITION_DWELL && !reqId.endsWith(EXIT_GEOFENCE_SUFFIX) -> {
                    if (!GeofenceStateStore.isInside(context, reqId)) {
                        GeofenceStateStore.setInside(context, reqId, true)
                        enters += reqId
                    }
                }
                gmsTransition == GmsGeofence.GEOFENCE_TRANSITION_EXIT && reqId.endsWith(EXIT_GEOFENCE_SUFFIX) -> {
                    val baseId = reqId.removeSuffix(EXIT_GEOFENCE_SUFFIX)
                    if (GeofenceStateStore.isInside(context, baseId)) exitCandidates += baseId
                }
            }
        }
        if (enters.isEmpty() && exitCandidates.isEmpty()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                enters.forEach { report(childId, it, GeofenceTransition.ENTER, lat, lng, now) }
                if (exitCandidates.isNotEmpty()) {
                    val fresh = freshLocation(context)
                    exitCandidates.forEach { baseId ->
                        if (confirmedOutside(context, baseId, fresh, triggerLoc)) {
                            GeofenceStateStore.setInside(context, baseId, false)
                            report(childId, baseId, GeofenceTransition.EXIT, lat, lng, now)
                        }
                        // else: phantom exit (still inside / unconfirmable) — keep state, no alert.
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun report(childId: String, geofenceId: String, transition: GeofenceTransition, lat: Double, lng: Double, now: Long) {
        geofenceRepository.reportEvent(
            childId,
            GeofenceEvent(geofenceId = geofenceId, transition = transition, latitude = lat, longitude = lng, timestampMillis = now),
        )
    }

    /** Only confirm a departure when a trustworthy fix is CONFIDENTLY beyond the exit buffer. */
    private fun confirmedOutside(context: Context, baseId: String, fresh: Location?, trigger: Location?): Boolean {
        val geo = GeofenceGeoCache.get(context, baseId)
            ?: return (trigger?.accuracy?.let { it <= EXIT_ACCURACY_MAX_METERS } ?: true) // no geometry → fall back to the accuracy gate
        val fix = fresh ?: trigger ?: return false
        if (!fix.hasAccuracy() || fix.accuracy > ACCURACY_MAX_METERS) return false
        val dist = GeoMath.distanceMeters(fix.latitude, fix.longitude, geo.lat, geo.lng)
        return dist - fix.accuracy > geo.radiusMeters + EXIT_BUFFER_METERS
    }

    @SuppressLint("MissingPermission")
    private suspend fun freshLocation(context: Context): Location? = runCatching {
        val client = LocationServices.getFusedLocationProviderClient(context)
        withTimeoutOrNull(FRESH_FIX_TIMEOUT_MS) {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
        }
    }.getOrNull()

    private companion object {
        // A fix fuzzier than this can't be trusted to decide inside/outside.
        const val ACCURACY_MAX_METERS = 120f
        // Departures need a confident fix (the 100 m exit buffer must mean something).
        const val EXIT_ACCURACY_MAX_METERS = 60f
        const val FRESH_FIX_TIMEOUT_MS = 8_000L // stay within the BroadcastReceiver goAsync budget
    }
}
