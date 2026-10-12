package com.shahaabapps.shield.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.location.Geofence as GmsGeofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.shahaabapps.core.model.Geofence
import com.shahaabapps.core.model.GeofenceTrigger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Suffix marking the outer "exit" geofence (radius + buffer). */
const val EXIT_GEOFENCE_SUFFIX = "#exit"

/** How far beyond a place's boundary the child must go before a "left" alert fires. */
const val EXIT_BUFFER_METERS = 100f

/**
 * How long the child must stay inside before an "arrived" alert fires (dwell).
 * Set to 3 minutes so merely walking past a place (e.g. a mosque on the route)
 * doesn't trigger a false arrival — only an actual stop does.
 */
const val DWELL_DELAY_MS = 180_000

/** Registers the parent's geofences with Play Services. */
class GeofenceRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client = LocationServices.getGeofencingClient(context)

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    @SuppressLint("MissingPermission")
    fun register(geofences: List<Geofence>) {
        client.removeGeofences(pendingIntent)
        val active = geofences.filter { it.active && it.id.isNotBlank() }
        if (active.isEmpty()) return

        val gmsGeofences = buildList {
            active.forEach { g ->
                val wantsEnter = g.trigger == GeofenceTrigger.ENTER || g.trigger == GeofenceTrigger.ENTER_EXIT
                val wantsExit = g.trigger == GeofenceTrigger.EXIT || g.trigger == GeofenceTrigger.ENTER_EXIT

                // Inner geofence → "arrived" via DWELL (must stay inside a short
                // while), so passing by doesn't trigger an alert.
                if (wantsEnter) {
                    add(
                        GmsGeofence.Builder()
                            .setRequestId(g.id)
                            .setCircularRegion(g.latitude, g.longitude, g.radiusMeters)
                            .setExpirationDuration(GmsGeofence.NEVER_EXPIRE)
                            .setTransitionTypes(GmsGeofence.GEOFENCE_TRANSITION_DWELL)
                            .setLoiteringDelay(DWELL_DELAY_MS)
                            .build(),
                    )
                }
                // Outer geofence (radius + 100 m) → "left" only once clearly away,
                // which avoids GPS-jitter alerts right at the boundary.
                if (wantsExit) {
                    add(
                        GmsGeofence.Builder()
                            .setRequestId(g.id + EXIT_GEOFENCE_SUFFIX)
                            .setCircularRegion(g.latitude, g.longitude, g.radiusMeters + EXIT_BUFFER_METERS)
                            .setExpirationDuration(GmsGeofence.NEVER_EXPIRE)
                            .setTransitionTypes(GmsGeofence.GEOFENCE_TRANSITION_EXIT)
                            .build(),
                    )
                }
            }
        }
        if (gmsGeofences.isEmpty()) return
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofences(gmsGeofences)
            .build()
        runCatching { client.addGeofences(request, pendingIntent) }
    }
}
