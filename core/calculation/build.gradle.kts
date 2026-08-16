plugins {
    alias(libs.plugins.kotlin.jvm)
    // Needed for the @Serializable fixture model in AlAdhanGoldenTest, not by production code.
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmToolchain(21)
    }
}

dependencies {
    api(projects.core.model)
    implementation(libs.adhan)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.serialization.json)
    testImplementation(libs.junit)
}
