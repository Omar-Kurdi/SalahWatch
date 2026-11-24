import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** The project's version catalog, so convention plugins read the same numbers the modules do. */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.intVersion(alias: String): Int =
    libs.findVersion(alias).get().requiredVersion.toInt()

/**
 * Android settings shared by every module, application or library.
 *
 * Java 17 is the *language and bytecode* level for our own code. That is independent of the JDK 21
 * toolchain the build runs on, which is required because `adhan2` ships Java 21 bytecode — D8
 * desugars that when producing DEX, so a dependency compiled to a higher target than our own code is
 * not a conflict.
 */
// AGP 9's CommonExtension is no longer generic and carries no block-style DSL functions (those
// moved to the concrete extensions), so the shared settings below go through the property getters.
internal fun Project.configureAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = intVersion("compileSdk")

        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
            // adhan2 and kotlinx-datetime reach for java.time APIs. minSdk is 29/30 so java.time is
            // present natively, but desugaring keeps the door open if minSdk is ever lowered and
            // costs nothing when unused.
            isCoreLibraryDesugaringEnabled = true
        }

        testOptions.unitTests.apply {
            // Android framework classes are stubs that throw by default in JVM unit tests.
            // Returning defaults instead lets code that merely logs be tested without either
            // mocking the framework or injecting a logger seam purely to satisfy the runtime.
            isReturnDefaultValues = true
        }

        packaging.apply {
            resources.excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/versions/9/previous-compilation-data.bin",
            )
        }
    }

    // Configured on the tasks rather than through `kotlinOptions`, which Kotlin 2.4 removed.
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    dependencies {
        add("coreLibraryDesugaring", libs.findLibrary("desugar-jdk-libs").get())
    }
}
