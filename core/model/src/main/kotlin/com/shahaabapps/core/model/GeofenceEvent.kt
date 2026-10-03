package com.shahaabapps.core.model

/** A geofence enter/exit event reported by the child device. */
data class GeofenceEvent(
    val id: String = "",
    val geofenceId: String = "",
    val geofenceName: String = "",
    val transition: GeofenceTransition = GeofenceTransition.ENTER,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestampMillis: Long = 0L,
)

enum class GeofenceTransition { ENTER, EXIT }
