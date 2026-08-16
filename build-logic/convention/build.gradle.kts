import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.salahwatch.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // compileOnly, not implementation: these plugins must NOT leak onto the consuming build's
    // classpath. If they did, the root build's `alias(libs.plugins.…) apply false` declarations
    // would fail with "already on the classpath with an unknown version". This is the reason
    // build-logic is an included build rather than buildSrc.
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "salahwatch.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "salahwatch.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidCompose") {
            id = "salahwatch.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
    }
}
