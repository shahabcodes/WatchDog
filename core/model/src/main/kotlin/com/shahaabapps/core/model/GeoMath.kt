package com.shahaabapps.core.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Geospatial helpers shared across apps. */
object GeoMath {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Great-circle distance between two points, in metres. */
    fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Ray-casting point-in-polygon test (planar in lat/lon — accurate enough for
     * the small areas geofences cover). [points] is the polygon's outline.
     */
    fun containsPoint(points: List<GeoPoint>, lat: Double, lon: Double): Boolean {
        if (points.size < 3) return false
        var inside = false
        var j = points.size - 1
        for (i in points.indices) {
            val yi = points[i].latitude
            val xi = points[i].longitude
            val yj = points[j].latitude
            val xj = points[j].longitude
            val denom = (yj - yi).let { if (it == 0.0) 1e-12 else it }
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / denom + xi) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * The centroid of a set of points and the smallest radius (metres) covering
     * all of them — used as a polygon's enforceable bounding circle.
     */
    fun boundingCircle(points: List<GeoPoint>): Triple<Double, Double, Float> {
        if (points.isEmpty()) return Triple(0.0, 0.0, 150f)
        val centerLat = points.sumOf { it.latitude } / points.size
        val centerLon = points.sumOf { it.longitude } / points.size
        var radius = 0.0
        for (p in points) {
            radius = max(radius, distanceMeters(centerLat, centerLon, p.latitude, p.longitude))
        }
        // Add a small margin so the whole shape sits inside the circle.
        return Triple(centerLat, centerLon, (radius * 1.05).toFloat().coerceAtLeast(50f))
    }
}
