plugins {
    id("salahwatch.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.salahwatch.data.prayersync"
}

dependencies {
    api(projects.data.prayerTimes)
    implementation(projects.core.preferences)

    implementation(libs.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
