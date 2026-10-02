plugins {
    id("fms.android.library")
    id("fms.android.hilt")
}

android {
    namespace = "com.fms.parentalcontrol.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
}
