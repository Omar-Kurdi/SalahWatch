plugins {
    id("salahwatch.android.library")
}

android {
    namespace = "com.salahwatch.data.location"
}

dependencies {
    api(projects.core.model)
    implementation(libs.play.services.location)
    implementation(libs.coroutines.play.services)
    implementation(libs.coroutines.core)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
