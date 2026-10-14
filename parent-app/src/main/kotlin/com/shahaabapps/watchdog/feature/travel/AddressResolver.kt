package com.shahaabapps.watchdog.feature.travel

import android.content.Context
import android.location.Geocoder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reverse-geocodes coordinates to a short street/area label using Android's
 * on-device [Geocoder] (free, no API key; network-backed). Used to name history
 * stops that don't fall inside a saved place — e.g. "Al Sadd St" or "Doha".
 *
 * Results are cached (and exposed as a flow) so the UI fills in labels as they
 * resolve and never re-looks-up the same spot. Degrades silently to no label on
 * devices without a geocoder backend.
 */
@Singleton
class AddressResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _addresses = MutableStateFlow<Map<String, String>>(emptyMap())
    val addresses: StateFlow<Map<String, String>> = _addresses.asStateFlow()
    private val inFlight = Collections.synchronizedSet(mutableSetOf<String>())

    /** A coarse cache key (~11 m) so nearby stops share one lookup. */
    fun keyOf(lat: Double, lng: Double): String = "%.4f,%.4f".format(lat, lng)

    /** Resolve a label for [lat]/[lng] if not already known / in flight. */
    fun request(lat: Double, lng: Double) {
        if (!Geocoder.isPresent()) return
        val key = keyOf(lat, lng)
        if (_addresses.value.containsKey(key) || !inFlight.add(key)) return
        scope.launch {
            val label = runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)
                    ?.firstOrNull()
                    ?.let(::shortLabel)
            }.getOrNull()
            if (!label.isNullOrBlank()) {
                _addresses.update { it + (key to label) }
            }
            inFlight.remove(key)
        }
    }

    private fun shortLabel(a: android.location.Address): String? {
        val street = a.thoroughfare
        val number = a.subThoroughfare
        return when {
            !street.isNullOrBlank() && !number.isNullOrBlank() -> "$number $street"
            !street.isNullOrBlank() -> street
            !a.subLocality.isNullOrBlank() -> a.subLocality
            !a.locality.isNullOrBlank() -> a.locality
            !a.featureName.isNullOrBlank() -> a.featureName
            else -> a.getAddressLine(0)
        }?.let { "Near $it" }
    }
}
