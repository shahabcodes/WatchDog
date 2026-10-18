package com.shahaabapps.shield.location

import android.content.Context
import android.util.Base64
import com.shahaabapps.core.common.crypto.DeviceKeyStore

/**
 * A small on-device cache of each active geofence's centre + radius, written by
 * [GeofenceSyncService] (which holds the decrypted geofences) so the OS-triggered
 * [GeofenceBroadcastReceiver] can verify a platform transition against the real
 * geometry — the coordinates are E2E-encrypted in Firestore, so the receiver
 * can't read them from the backend. Device-local only; never uploaded.
 *
 * The cached coordinates are themselves sensitive, so each value is sealed with
 * the hardware-backed master key ([DeviceKeyStore.sealLocal]) rather than stored
 * as cleartext. Values written by an older (plaintext) build are still read via a
 * fallback and get re-sealed on the next [save].
 */
object GeofenceGeoCache {
    private const val PREFS = "geofence_geo"

    data class Circle(val lat: Double, val lng: Double, val radiusMeters: Double)

    fun save(context: Context, geofences: List<Triple<String, Pair<Double, Double>, Double>>) {
        runCatching {
            val keyStore = DeviceKeyStore(context.applicationContext)
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val editor = prefs.edit().clear()
            geofences.forEach { (id, latlng, radius) ->
                if (id.isNotBlank()) {
                    val plain = "${latlng.first},${latlng.second},$radius"
                    editor.putString(id, seal(keyStore, plain))
                }
            }
            editor.apply()
        }
    }

    fun get(context: Context, geofenceId: String): Circle? = runCatching {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(geofenceId, null) ?: return null
        val decoded = open(DeviceKeyStore(context.applicationContext), raw)
        val parts = decoded.split(",")
        Circle(parts[0].toDouble(), parts[1].toDouble(), parts[2].toDouble())
    }.getOrNull()

    private fun seal(keyStore: DeviceKeyStore, plain: String): String =
        Base64.encodeToString(keyStore.sealLocal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)

    /** Decrypt a sealed value; fall back to treating it as legacy cleartext. */
    private fun open(keyStore: DeviceKeyStore, stored: String): String =
        runCatching {
            String(keyStore.openLocal(Base64.decode(stored, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrDefault(stored)
}
