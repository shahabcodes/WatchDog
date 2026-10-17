package com.shahaabapps.core.firebase.geofence

import android.content.Context

/**
 * A tiny on-device cache of geofence (saved-place) names, keyed by the plaintext
 * geofence id. Place names are end-to-end encrypted, so a background push handler
 * can't reliably decrypt them in a cold process. Whenever the app decrypts a
 * geofence in the foreground its name is cached here, so the geofence push can
 * name the place by id without any decryption.
 */
object GeofenceNameCache {
    private const val PREFS = "geofence_names"

    private fun key(childId: String, geofenceId: String) = "$childId/$geofenceId"

    fun put(context: Context, childId: String, geofenceId: String, name: String) {
        if (geofenceId.isBlank() || name.isBlank()) return
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(key(childId, geofenceId), name).apply()
        }
    }

    fun get(context: Context, childId: String, geofenceId: String): String? = runCatching {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(childId, geofenceId), null)?.ifBlank { null }
    }.getOrNull()
}
