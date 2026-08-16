plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmToolchain(21)
    }
}

dependencies {
    api(libs.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
