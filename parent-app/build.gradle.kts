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

    // OpenFreeMap tiles via MapLibre (free, no API key)
    implementation(libs.maplibre.android.sdk)
    // Location + geofencing (Play Services)
    implementation(libs.play.services.location)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
}
