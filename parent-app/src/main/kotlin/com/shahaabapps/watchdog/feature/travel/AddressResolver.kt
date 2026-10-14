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
                val results = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 5)
                results?.let(::bestLabel)
            }.getOrNull()
            if (!label.isNullOrBlank()) {
                _addresses.update { it + (key to label) }
            }
            inFlight.remove(key)
        }
    }

    /**
     * Picks the most street-specific label across the geocoder's candidates: a
     * street (with number when known) first, then neighbourhood, then city.
     */
    private fun bestLabel(results: List<android.location.Address>): String? {
        results.firstOrNull { !it.thoroughfare.isNullOrBlank() }?.let { a ->
            val number = a.subThoroughfare
            return if (!number.isNullOrBlank()) "$number ${a.thoroughfare}" else a.thoroughfare
        }
        results.firstOrNull { !it.subLocality.isNullOrBlank() }?.let { return it.subLocality }
        val first = results.firstOrNull() ?: return null
        return first.locality ?: first.featureName ?: first.getAddressLine(0)
    }
}
