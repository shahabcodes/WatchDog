plugins {
    id("fms.android.library")
    id("fms.android.compose")
}

android {
    namespace = "com.shahaabapps.core.ui"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.coil.compose)
}
