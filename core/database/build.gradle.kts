plugins {
    id("salahwatch.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.salahwatch.core.database"

    // Room's exported schema JSON is checked in, so schema changes show up in review.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    api(projects.core.model)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
