package com.shahaabapps.core.firebase.geofence

import android.content.Context
import android.util.Base64
import com.shahaabapps.core.common.crypto.DeviceKeyStore

/**
 * A tiny on-device cache of geofence (saved-place) names, keyed by the plaintext
 * geofence id. Place names are end-to-end encrypted, so a background push handler
 * can't reliably decrypt them in a cold process. Whenever the app decrypts a
 * geofence in the foreground its name is cached here, so the geofence push can
 * name the place by id without any decryption.
 *
 * Place names are sensitive, so each cached value is sealed with the device's
 * hardware-backed master key ([DeviceKeyStore.sealLocal]) rather than stored in
 * cleartext. Values written by an older (plaintext) build are still read via a
 * fallback and get re-sealed on the next [put].
 */
object GeofenceNameCache {
    private const val PREFS = "geofence_names"

    private fun key(childId: String, geofenceId: String) = "$childId/$geofenceId"

    fun put(context: Context, childId: String, geofenceId: String, name: String) {
        if (geofenceId.isBlank() || name.isBlank()) return
        runCatching {
            val sealed = seal(DeviceKeyStore(context.applicationContext), name)
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(key(childId, geofenceId), sealed).apply()
        }
    }

    fun get(context: Context, childId: String, geofenceId: String): String? = runCatching {
        val stored = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(childId, geofenceId), null) ?: return null
        open(DeviceKeyStore(context.applicationContext), stored).ifBlank { null }
    }.getOrNull()

    private fun seal(keyStore: DeviceKeyStore, plain: String): String =
        Base64.encodeToString(keyStore.sealLocal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)

    private fun open(keyStore: DeviceKeyStore, stored: String): String =
        runCatching {
            String(keyStore.openLocal(Base64.decode(stored, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrDefault(stored)
}
