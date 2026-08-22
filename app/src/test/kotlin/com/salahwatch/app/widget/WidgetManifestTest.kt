package com.salahwatch.app.widget

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards a failure that produces no error anywhere.
 *
 * [BasePrayerWidgetProvider.REFRESH_ON] decides what a receiver *acts* on; the manifest decides what
 * it is ever *handed*. Adding an action to one and not the other is silent in every build, in
 * detekt and in lint — the broadcast simply never arrives, and the only symptom is a widget that
 * stops updating after a reboot, which nobody notices until it matters.
 *
 * Both widgets are checked, because a second receiver is exactly the kind of thing that gets added
 * with three of the seven filters copied across.
 */
class WidgetManifestTest {

    private val manifest: String by lazy {
        val file = sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).firstOrNull { it.exists() }

        requireNotNull(file) { "Could not locate the app manifest from ${File(".").absolutePath}" }
            .readText()
    }

    private fun filterOf(receiver: String): String = manifest
        .substringAfter("android:name=\".widget.$receiver\"")
        .substringAfter("<intent-filter>")
        .substringBefore("</intent-filter>")

    @Test
    fun `every action a receiver acts on is one the manifest lets through`() {
        for (receiver in RECEIVERS) {
            val filter = filterOf(receiver)
            val missing = BasePrayerWidgetProvider.REFRESH_ON.filterNot { action ->
                filter.contains("android:name=\"$action\"")
            }

            assertEquals(
                emptyList(),
                missing,
                "$receiver handles these in Kotlin but the manifest never delivers them",
            )
        }
    }

    @Test
    fun `the manifest declares nothing a receiver would ignore`() {
        for (receiver in RECEIVERS) {
            val declared = ACTION_PATTERN.findAll(filterOf(receiver))
                .map { it.groupValues[1] }
                .toSet()

            assertEquals(
                emptySet(),
                declared - BasePrayerWidgetProvider.REFRESH_ON,
                "$receiver is woken for these and does nothing with them",
            )
        }
    }

    @Test
    fun `resizing is not declared, because the platform delivers it regardless`() {
        // ACTION_APPWIDGET_OPTIONS_CHANGED goes straight to onAppWidgetOptionsChanged. Listing it
        // would be harmless but misleading, and the assertion above would then fail — this test
        // exists so that failure reads as intent rather than as an oversight.
        assertTrue(
            RECEIVERS.none { filterOf(it).contains("APPWIDGET_OPTIONS_CHANGED") },
            "The options-changed action needs no filter entry",
        )
    }

    @Test
    fun `both widgets can rearm their alarms after a reboot`() {
        assertTrue(
            manifest.contains("android.permission.RECEIVE_BOOT_COMPLETED"),
            "Without this permission the BOOT_COMPLETED filters are never honoured, and a placed " +
                "widget freezes on the previous day's times after a restart",
        )
    }

    @Test
    fun `each widget declares its own provider metadata`() {
        assertTrue(manifest.contains("@xml/prayer_widget_info"))
        assertTrue(manifest.contains("@xml/prayer_strip_widget_info"))
    }

    private companion object {
        val RECEIVERS = listOf("PrayerWidgetProvider", "PrayerStripWidgetProvider")
        val ACTION_PATTERN = Regex("""<action android:name="([^"]+)" */>""")
    }
}
