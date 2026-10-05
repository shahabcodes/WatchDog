package com.shahaabapps.watchdog.feature.geofence

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalAirport
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** The selectable icons for a saved place, keyed by a stable string stored on the geofence. */
object PlaceIcons {

    val options: List<Pair<String, ImageVector>> = listOf(
        "home" to Icons.Filled.Home,
        "school" to Icons.Filled.School,
        "work" to Icons.Filled.Work,
        "mosque" to Icons.Filled.Mosque,
        "gym" to Icons.Filled.FitnessCenter,
        "park" to Icons.Filled.Park,
        "shop" to Icons.Filled.ShoppingBag,
        "food" to Icons.Filled.Restaurant,
        "hospital" to Icons.Filled.LocalHospital,
        "airport" to Icons.Filled.LocalAirport,
        "metro" to Icons.Filled.DirectionsSubway,
        "friends" to Icons.Filled.Groups,
        "place" to Icons.Filled.Place,
    )

    private val byKey = options.toMap()

    fun forKey(key: String?): ImageVector = byKey[key] ?: Icons.Filled.Place
}
