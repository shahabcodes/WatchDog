package com.shahaabapps.core.model

/**
 * A geofence authored by a parent and enforced on the child's device.
 *
 * For [GeofenceShape.CIRCLE], [latitude]/[longitude]/[radiusMeters] define it directly.
 * For [GeofenceShape.POLYGON], [points] hold the drawn shape (for display) and
 * [latitude]/[longitude]/[radiusMeters] hold its bounding circle, which Android's
 * (circle-only) geofencing uses for enter/exit triggering.
 */
data class Geofence(
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Float = 150f,
    val trigger: GeofenceTrigger = GeofenceTrigger.ENTER_EXIT,
    val active: Boolean = true,
    val shape: GeofenceShape = GeofenceShape.CIRCLE,
    val points: List<GeoPoint> = emptyList(),
)

enum class GeofenceShape { CIRCLE, POLYGON }

data class GeoPoint(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
)

enum class GeofenceTrigger {
    ENTER,
    EXIT,
    ENTER_EXIT,
}
