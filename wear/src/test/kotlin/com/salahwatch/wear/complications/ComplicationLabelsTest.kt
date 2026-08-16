package com.salahwatch.wear.complications

import com.salahwatch.core.model.Prayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the translation seam, and the seven-character budget it has to fit inside.
 *
 * `SHORT_TEXT` caps the title at seven characters and enforces it on the title independently of the
 * text, so every word that can appear as a title has to be checked — not just the prayer names.
 * Both halves of this are easy to break silently: the first by reaching for `Prayer.shortName` out
 * of habit, the second by adding a language whose words are longer. Without a watch to look at,
 * this test is the only thing that catches either.
 */
class ComplicationLabelsTest {

    private val arabic = ComplicationLabels(
        prayerNames = mapOf(
            Prayer.FAJR to "الفجر",
            Prayer.SUNRISE to "الشروق",
            Prayer.DHUHR to "الظهر",
            Prayer.ASR to "العصر",
            Prayer.MAGHRIB to "المغرب",
            Prayer.ISHA to "العشاء",
        ),
        adhan = "الأذان",
        iqama = "الإقامة",
        pray = "الصلاة",
        estimated = "تقديري",
    )

    private val english = ComplicationLabels.Fallback

    @Test
    fun `every Arabic prayer name fits the SHORT_TEXT budget`() {
        for (prayer in Prayer.entries) {
            val name = arabic.nameOf(prayer)
            assertTrue(
                name.length <= Prayer.MAX_SHORT_TEXT_LENGTH,
                "$name is ${name.length} characters; the watch face would truncate it",
            )
        }
    }

    @Test
    fun `the other two title words fit the budget in both languages`() {
        // A complication's title is not always a prayer name: it becomes "Iqama" once the Adhan has
        // been called, and "Pray" once the congregation has begun. Arabic الإقامة is exactly seven,
        // with no headroom, which is precisely why this is asserted rather than assumed.
        for (labels in listOf(english, arabic)) {
            for (word in listOf(labels.iqama, labels.pray)) {
                assertTrue(
                    word.length <= Prayer.MAX_SHORT_TEXT_LENGTH,
                    "$word is ${word.length} characters; the watch face would truncate it",
                )
            }
        }
    }

    @Test
    fun `the fallback is only the untranslated identifiers`() {
        // Used for previews and tests, never for a running complication.
        assertEquals("MAGHRIB", ComplicationLabels.Fallback.nameOf(Prayer.MAGHRIB))
    }
}
