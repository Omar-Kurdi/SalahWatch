plugins {
    id("salahwatch.android.application")
    id("salahwatch.android.compose")
}

/**
 * The third-party notices, compiled in as a raw resource.
 *
 * `THIRD_PARTY_NOTICES.txt` at the repo root is the single canonical copy, synced into this
 * module's generated resources rather than duplicated per app. Two hand-maintained copies of a
 * legal notice drift, and the one that drifts is the one nobody reads until it matters.
 *
 * MIT (adhan2) requires its notice to accompany every copy of the software, and Apache 2.0 section
 * 4(a) requires shipping the licence itself — so this has to be inside the APK, not merely in the
 * repository.
 */
val syncThirdPartyNotices by tasks.registering(Sync::class) {
    from(rootProject.file("THIRD_PARTY_NOTICES.txt")) {
        into("raw")
        // Resource file names must be lowercase and free of punctuation beyond the extension.
        rename { "third_party_notices.txt" }
    }
    into(layout.buildDirectory.dir("generated/notices/res"))
}

android {
    sourceSets["main"].res.srcDir(layout.buildDirectory.dir("generated/notices/res"))

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

// srcDir() above takes a path, not a task, so it carries no dependency of its own — without this
// the sync never runs and the notices are silently absent from the APK. They were, once.
tasks.named("preBuild") { dependsOn(syncThirdPartyNotices) }
