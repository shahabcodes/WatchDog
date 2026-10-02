plugins {
    id("fms.android.application")
    id("fms.android.compose")
    id("fms.android.hilt")
    id("fms.android.firebase")
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.fms.parentalcontrol.parent"

    defaultConfig {
        applicationId = "com.fms.parentalcontrol.parent"
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

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
}
