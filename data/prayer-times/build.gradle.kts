plugins {
    id("salahwatch.android.library")
}

android {
    namespace = "com.salahwatch.data.prayertimes"
}

// Deliberately has no networking and no WorkManager. This is the module the watch depends on, and
// keeping fetching out of it is what stops OkHttp's INTERNET permission and WorkManager's WAKE_LOCK
// from being merged into the watch APK. Fetching lives in :data:prayer-sync, which only the phone
// depends on.
dependencies {
    api(projects.core.model)
    api(projects.core.calculation)
    api(projects.core.database)
    api(projects.core.preferences)

    implementation(libs.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
