package com.shahaabapps.core.model

/**
 * A geofence authored by a parent and enforced on the child's device.
 */
data class Geofence(
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Float = 150f,
    val trigger: GeofenceTrigger = GeofenceTrigger.ENTER_EXIT,
    val active: Boolean = true,
)

enum class GeofenceTrigger {
    ENTER,
    EXIT,
    ENTER_EXIT,
}
