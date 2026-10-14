plugins {
    id("fms.android.application")
    id("fms.android.compose")
    id("fms.android.hilt")
    id("fms.android.firebase")
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.shahaabapps.shield"

    defaultConfig {
        applicationId = "com.shahaabapps.shield"
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

    // QR generation (child shows its pairing code)
    implementation(libs.zxing.core)

    // Location + geofencing
    implementation(libs.play.services.location)

    // Background backstop: periodic location sync if the foreground service is killed.
    implementation(libs.androidx.work.runtime.ktx)

    // Durable on-device queue for location points (offline-safe buffer).
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    add("ksp", libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
}
