plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.ricketymajor.spindle"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.ricketymajor.spindle"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1"
        // ponytail: only the user's phone (arm64); add "x86_64" back if an emulator is ever needed.
        ndk { abiFilters += "arm64-v8a" }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.taglib)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
}
