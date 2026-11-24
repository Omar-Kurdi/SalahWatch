import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
}

// Detekt is applied per module rather than once at the root scanning `projectDir`.
// Scanning the root directory sweeps every module's `build/` output into detekt's inputs, which
// makes Gradle (correctly) reject the task for consuming another task's output without declaring a
// dependency on it. Applying it per module means each detekt task uses that module's real Kotlin
// source sets and nothing else.
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        parallel = true
    }
}
