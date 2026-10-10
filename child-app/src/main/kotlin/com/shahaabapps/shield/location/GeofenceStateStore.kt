package com.shahaabapps.shield.location

import android.content.Context

/**
 * Remembers whether the child is currently "inside" each place, so a "left" alert
 * only fires after a matching "arrived" — never from merely passing nearby (which
 * would otherwise trigger an outer-fence EXIT with no preceding DWELL/arrival).
 */
object GeofenceStateStore {
    private const val PREFS = "geofence_state"
    private const val KEY_PREFIX = "inside_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isInside(context: Context, geofenceId: String): Boolean =
        prefs(context).getBoolean(KEY_PREFIX + geofenceId, false)

    fun setInside(context: Context, geofenceId: String, inside: Boolean) {
        prefs(context).edit().putBoolean(KEY_PREFIX + geofenceId, inside).apply()
    }
}
