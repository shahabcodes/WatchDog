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

        val gmsGeofences = active.map { g ->
            GmsGeofence.Builder()
                .setRequestId(g.id)
                .setCircularRegion(g.latitude, g.longitude, g.radiusMeters)
                .setExpirationDuration(GmsGeofence.NEVER_EXPIRE)
                .setTransitionTypes(transitionMask(g.trigger))
                .build()
        }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofences(gmsGeofences)
            .build()
        runCatching { client.addGeofences(request, pendingIntent) }
    }

    private fun transitionMask(trigger: GeofenceTrigger): Int = when (trigger) {
        GeofenceTrigger.ENTER -> GmsGeofence.GEOFENCE_TRANSITION_ENTER
        GeofenceTrigger.EXIT -> GmsGeofence.GEOFENCE_TRANSITION_EXIT
        GeofenceTrigger.ENTER_EXIT ->
            GmsGeofence.GEOFENCE_TRANSITION_ENTER or GmsGeofence.GEOFENCE_TRANSITION_EXIT
    }
}
