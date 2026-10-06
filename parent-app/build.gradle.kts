plugins {
    id("fms.android.application")
    id("fms.android.compose")
    id("fms.android.hilt")
    id("fms.android.firebase")
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.shahaabapps.watchdog"

    defaultConfig {
        applicationId = "com.shahaabapps.watchdog"
        // Google Maps key for the geofence map; supplied via env (CI) or a
        // -PMAPS_API_KEY gradle property locally. Empty = map tiles won't load.
        manifestPlaceholders["MAPS_API_KEY"] =
            System.getenv("MAPS_API_KEY")
                ?: (project.findProperty("MAPS_API_KEY") as String?)
                ?: ""
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:firebase"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.coil.compose)

    // QR scanning (parent scans the child's pairing code)
    implementation(libs.zxing.android.embedded)

    // Map for drawing geofences
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    // OpenFreeMap tiles via MapLibre (free, no API key)
    implementation(libs.maplibre.android.sdk)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
}
