plugins {
    id("salahwatch.android.application")
    id("salahwatch.android.compose")
}

android {
    namespace = "com.salahwatch.wear"

    defaultConfig {
        applicationId = "com.salahwatch"
        // Wear OS 3 (Galaxy Watch 4 onwards). See RESEARCH.md §1.1.
        minSdk = libs.versions.minSdkWear.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            // Signed with the debug key so release builds can be sideloaded for testing without a
            // keystore. This is fine for personal installs and is NOT publishable: Play requires an
            // upload key you control, and an app signed with the debug key cannot be updated by one
            // signed properly later. Replace this with a real signingConfig before any distribution.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.calculation)
    implementation(projects.data.prayerTimes)
    implementation(projects.data.sync)
    // The listener service subclasses WearableListenerService directly.
    implementation(libs.play.services.wearable)

    implementation(libs.wear.complications.datasource)
    implementation(libs.protolayout.expression)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.coroutines.android)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}

