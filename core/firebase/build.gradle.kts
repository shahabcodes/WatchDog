plugins {
    id("fms.android.library")
    id("fms.android.hilt")
    id("fms.android.firebase")
}

android {
    namespace = "com.shahaabapps.core.firebase"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
