import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Shared configuration for every Android library module.
 *
 * Library modules take the *lower* of the two application minSdks so that both the phone app (29)
 * and the wear app (30) can consume them.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        // No org.jetbrains.kotlin.android: AGP 9 compiles Kotlin itself, and applying it fails.

        extensions.configure<LibraryExtension> {
            defaultConfig.minSdk = intVersion("minSdkPhone")
            configureAndroid(this)
        }
    }
}
