package com.shahaabapps.shield.location

import android.content.Context

/**
 * A small on-device cache of each active geofence's centre + radius, written by
 * [GeofenceSyncService] (which holds the decrypted geofences) so the OS-triggered
 * [GeofenceBroadcastReceiver] can verify a platform transition against the real
 * geometry — the coordinates are E2E-encrypted in Firestore, so the receiver
 * can't read them from the backend. Device-local only; never uploaded.
 */
object GeofenceGeoCache {
    private const val PREFS = "geofence_geo"

    data class Circle(val lat: Double, val lng: Double, val radiusMeters: Double)

    fun save(context: Context, geofences: List<Triple<String, Pair<Double, Double>, Double>>) {
        runCatching {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val editor = prefs.edit().clear()
            geofences.forEach { (id, latlng, radius) ->
                if (id.isNotBlank()) editor.putString(id, "${latlng.first},${latlng.second},$radius")
            }
            editor.apply()
        }
    }

    fun get(context: Context, geofenceId: String): Circle? = runCatching {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(geofenceId, null) ?: return null
        val parts = raw.split(",")
        Circle(parts[0].toDouble(), parts[1].toDouble(), parts[2].toDouble())
    }.getOrNull()
}
