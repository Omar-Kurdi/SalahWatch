plugins {
    id("salahwatch.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.salahwatch.data.sync"
}

dependencies {
    api(projects.core.model)
    api(projects.core.preferences)
    implementation(projects.data.prayerTimes)

    implementation(libs.play.services.wearable)
    implementation(libs.coroutines.play.services)
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
