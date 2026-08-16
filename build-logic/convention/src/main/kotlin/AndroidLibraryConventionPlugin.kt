import com.android.build.gradle.LibraryExtension
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
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<LibraryExtension> {
            defaultConfig.minSdk = intVersion("minSdkPhone")
            configureAndroid(this)
        }
    }
}
