plugins {
    id("salahwatch.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.salahwatch.core.preferences"
}

dependencies {
    api(projects.core.model)
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
