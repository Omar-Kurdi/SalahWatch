plugins {
    id("salahwatch.android.application")
    id("salahwatch.android.compose")
}

android {
    namespace = "com.salahwatch.app"

    defaultConfig {
        applicationId = "com.salahwatch"
        minSdk = libs.versions.minSdkPhone.get().toInt()
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
    implementation(projects.data.prayerSync)
    implementation(projects.data.location)
    implementation(projects.data.sync)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.coroutines.android)
    implementation(libs.work.runtime.ktx)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
