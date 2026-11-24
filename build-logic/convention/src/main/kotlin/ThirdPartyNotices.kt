import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

/**
 * The third-party notices, compiled into every app as a raw resource.
 *
 * `THIRD_PARTY_NOTICES.txt` at the repo root is the single canonical copy, copied into each app's
 * generated resources rather than duplicated per app. Two hand-maintained copies of a legal notice
 * drift, and the one that drifts is the one nobody reads until it matters.
 *
 * MIT (adhan2) requires its notice to accompany every copy of the software, and Apache 2.0 section
 * 4(a) requires shipping the licence itself — so this has to be inside the APK, not merely in the
 * repository.
 *
 * Registered through the Variant API: AGP 9 refuses a provider added to `sourceSets.res`, and this
 * way the resource merge depends on the copy by itself. The old `preBuild` wiring existed because
 * a plain `srcDir` carried no task dependency, and the notices were once silently missing from the
 * APK for exactly that reason.
 */
internal fun Project.bundleThirdPartyNotices() {
    val copyNotices = tasks.register<CopyThirdPartyNotices>("copyThirdPartyNotices") {
        notices.set(rootProject.layout.projectDirectory.file("THIRD_PARTY_NOTICES.txt"))
        outputDir.set(layout.buildDirectory.dir("generated/notices/res"))
    }
    extensions.configure<ApplicationAndroidComponentsExtension> {
        onVariants { variant ->
            variant.sources.res?.addGeneratedSourceDirectory(copyNotices, CopyThirdPartyNotices::outputDir)
        }
    }
}

/** Copies the notices into a resource root as `raw/third_party_notices.txt`. */
abstract class CopyThirdPartyNotices : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val notices: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val raw = outputDir.get().dir("raw").asFile
        raw.deleteRecursively()
        raw.mkdirs()
        // Resource file names must be lowercase and free of punctuation beyond the extension.
        notices.get().asFile.copyTo(raw.resolve("third_party_notices.txt"))
    }
}
