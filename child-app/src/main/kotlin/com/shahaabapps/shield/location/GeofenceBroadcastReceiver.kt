package com.shahaabapps.shield.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence as GmsGeofence
import com.google.android.gms.location.GeofencingEvent
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.geofence.GeofenceRepository
import com.shahaabapps.core.model.GeofenceEvent
import com.shahaabapps.core.model.GeofenceTransition
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

        // Map Play Services transitions to our model, honouring the inner/outer
        // geofence design: DWELL on the inner fence = "arrived", EXIT on the
        // outer (radius + buffer) fence = "left". Raw ENTER is ignored.
        val reports = triggering.mapNotNull { gf ->
            val reqId = gf.requestId
            when {
                gmsTransition == GmsGeofence.GEOFENCE_TRANSITION_DWELL && !reqId.endsWith(EXIT_GEOFENCE_SUFFIX) ->
                    reqId to GeofenceTransition.ENTER
                gmsTransition == GmsGeofence.GEOFENCE_TRANSITION_EXIT && reqId.endsWith(EXIT_GEOFENCE_SUFFIX) ->
                    reqId.removeSuffix(EXIT_GEOFENCE_SUFFIX) to GeofenceTransition.EXIT
                else -> null
            }
        }
        if (reports.isEmpty()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                reports.forEach { (geofenceId, transition) ->
                    geofenceRepository.reportEvent(
                        childId,
                        GeofenceEvent(
                            geofenceId = geofenceId,
                            transition = transition,
                            latitude = lat,
                            longitude = lng,
                            timestampMillis = now,
                        ),
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
