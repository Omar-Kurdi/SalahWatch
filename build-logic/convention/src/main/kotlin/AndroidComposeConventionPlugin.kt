import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer

/**
 * Enables Compose for a module that has already applied one of the Android convention plugins.
 *
 * Applied separately because not every module needs Compose — the complication data sources in the
 * wear module render nothing themselves, so keeping Compose out of pure-logic modules keeps their
 * build (and the resulting APK) smaller.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.findAndroidExtension()?.buildFeatures?.compose = true
    }
}

private fun ExtensionContainer.findAndroidExtension(): CommonExtension<*, *, *, *, *, *>? =
    findByName("android") as? CommonExtension<*, *, *, *, *, *>
