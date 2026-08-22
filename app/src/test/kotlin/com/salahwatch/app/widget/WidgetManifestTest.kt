package com.salahwatch.app.widget

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards a failure that produces no error anywhere.
 *
 * [PrayerWidgetProvider.REFRESH_ON] decides what the receiver *acts* on; the manifest decides what
 * it is ever *handed*. Adding an action to one and not the other is silent in every build, in
 * detekt and in lint — the broadcast simply never arrives, and the only symptom is a widget that
 * stops updating after a reboot, which is exactly the kind of thing nobody notices until it matters.
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

    /** The `<intent-filter>` of the widget receiver, and nothing else in the file. */
    private val widgetFilter: String by lazy {
        val receiver = manifest.substringAfter("android:name=\".widget.PrayerWidgetProvider\"")
        receiver.substringAfter("<intent-filter>").substringBefore("</intent-filter>")
    }

    @Test
    fun `every action the receiver acts on is one the manifest lets through`() {
        val missing = PrayerWidgetProvider.REFRESH_ON.filterNot { action ->
            widgetFilter.contains("android:name=\"$action\"")
        }

        assertEquals(
            emptyList(),
            missing,
            "These actions are handled in Kotlin but not declared in the manifest, so they will " +
                "never be delivered",
        )
    }

    @Test
    fun `the manifest declares nothing the receiver would ignore`() {
        val declared = ACTION_PATTERN.findAll(widgetFilter).map { it.groupValues[1] }.toSet()

        assertEquals(
            emptySet(),
            declared - PrayerWidgetProvider.REFRESH_ON,
            "These actions wake the receiver for nothing",
        )
    }

    @Test
    fun `the widget can rearm its alarm after a reboot`() {
        assertTrue(
            manifest.contains("android.permission.RECEIVE_BOOT_COMPLETED"),
            "Without this permission the BOOT_COMPLETED filter above is never honoured, and a " +
                "placed widget freezes on the previous day's times after a restart",
        )
    }

    private companion object {
        val ACTION_PATTERN = Regex("""<action android:name="([^"]+)" */>""")
    }
}
