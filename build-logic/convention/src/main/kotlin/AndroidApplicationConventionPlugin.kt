import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Shared configuration for the phone and watch application modules.
 *
 * `minSdk` and `targetSdk` are deliberately left to each module: the phone targets API 29+ while
 * the watch targets API 30+ (Wear OS 3, i.e. Galaxy Watch 4 onwards).
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        // No org.jetbrains.kotlin.android: AGP 9 compiles Kotlin itself, and applying it fails.

        extensions.configure<ApplicationExtension> {
            defaultConfig.targetSdk = intVersion("targetSdk")
            configureAndroid(this)
        }
        bundleThirdPartyNotices()
    }
}
