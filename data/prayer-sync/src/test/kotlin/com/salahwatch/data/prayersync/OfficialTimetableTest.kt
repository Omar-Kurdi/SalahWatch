package com.salahwatch.data.prayersync

import com.salahwatch.core.model.OfficialArea
import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.ScheduleSource
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the bundled timetables and the parser that reads them.
 *
 * Both halves matter and for different reasons. The parser is the only thing standing between a
 * transcription slip and a user praying at the wrong time, because every value in these files is a
 * plausible time — nothing downstream can tell a swapped column from a correct one. And the shipped
 * assets are checked here, against the real files, because a bundle that fails to parse on a device
 * degrades silently to calculation: the app keeps working, the times quietly stop being official,
 * and nobody finds out until they are compared against a printed table.
 */
class OfficialTimetableTest {

    private fun file(months: String, zone: String = "Asia/Dubai") = """
        {"area":"Test","slug":"test","zone":"$zone","source":"s","retrieved":"r",
         "order":["fajr","sunrise","dhuhr","asr","maghrib","isha"],"months":{$months}}
    """.trimIndent()

    private val oneDay = """"2026-8":"043905531220154618401955""""

    @Test
    fun `parses a day into six prayers`() {
        val parsed = assertNotNull(OfficialTimetableParser.parse(file(oneDay)))
        assertEquals(1, parsed.size)

        val day = parsed.single()
        assertEquals(LocalDate.of(2026, 8, 1), day.date)
        assertEquals(ScheduleSource.OFFICIAL, day.source)
        assertEquals(
            listOf(
                Prayer.FAJR,
                Prayer.SUNRISE,
                Prayer.DHUHR,
                Prayer.ASR,
                Prayer.MAGHRIB,
                Prayer.ISHA,
            ),
            day.times.map { it.prayer },
        )
    }

    @Test
    fun `resolves times in the file's zone, not the machine's`() {
        val day = assertNotNull(OfficialTimetableParser.parse(file(oneDay))).single()
        val fajr = day.times.first { it.prayer == Prayer.FAJR }.adhan
        assertEquals("04:39", fajr.atZone(ZoneId.of("Asia/Dubai")).toLocalTime().toString())
    }

    @Test
    fun `an official day carries no Iqama of its own`() {
        // A published table states the Adhan; the congregation time is the user's setting. If this
        // ever changed, IqamaCalculator's output would be silently overridden for this source only.
        val day = assertNotNull(OfficialTimetableParser.parse(file(oneDay))).single()
        assertTrue(day.times.all { it.iqama == null })
    }

    @Test
    fun `rejects a day whose times run backwards`() {
        // Maghrib before Asr — the shape a swapped pair of columns takes.
        assertNull(
            OfficialTimetableParser.parse(file(""""2026-8":"043905531220154618401555"""")),
        )
    }

    @Test
    fun `rejects a record of the wrong length`() {
        assertNull(OfficialTimetableParser.parse(file(""""2026-8":"04390553122015461840"""")))
    }

    @Test
    fun `rejects an impossible time`() {
        assertNull(
            OfficialTimetableParser.parse(file(""""2026-8":"049905531220154618401955"""")),
        )
    }

    @Test
    fun `rejects a day that does not exist in its month`() {
        val thirtyDaysInFebruary = (1..30).joinToString(",") { "043905531220154618401955" }
        assertNull(OfficialTimetableParser.parse(file(""""2026-2":"$thirtyDaysInFebruary"""")))
    }

    @Test
    fun `rejects an unparseable month key`() {
        assertNull(OfficialTimetableParser.parse(file(""""August":"043905531220154618401955"""")))
    }

    @Test
    fun `rejects an unknown zone`() {
        assertNull(OfficialTimetableParser.parse(file(oneDay, zone = "Mars/Olympus")))
    }

    @Test
    fun `every bundled area ships a timetable that parses`() {
        for (area in OfficialArea.entries) {
            val asset = File("src/main/assets/official/${area.slug}.json")
            assertTrue(asset.exists(), "missing bundled timetable for ${area.slug}")

            val parsed = OfficialTimetableParser.parse(asset.readText())
            assertNotNull(parsed, "bundled timetable for ${area.slug} failed validation")
            assertTrue(parsed.size > MINIMUM_BUNDLED_DAYS, "${area.slug} covers only ${parsed.size}")
        }
    }

    @Test
    fun `bundled timetables have no gaps between their first and last day`() {
        // A dropped day would not fail validation on its own: every remaining record is well
        // formed. It would show up in use as one day of calculated times in the middle of a run of
        // official ones, which is exactly the kind of thing nobody reports.
        for (area in OfficialArea.entries) {
            val parsed = assertNotNull(
                OfficialTimetableParser.parse(File("src/main/assets/official/${area.slug}.json").readText()),
            )
            val expected = java.time.temporal.ChronoUnit.DAYS
                .between(parsed.first().date, parsed.last().date) + 1
            assertEquals(expected.toInt(), parsed.size, "${area.slug} has a gap")
        }
    }

    @Test
    fun `bundled days match the authority's published table`() {
        // Spot values read from Awqaf's own published tables when these bundles were captured. They
        // exist so that a future refresh cannot quietly shift the data: a regenerated file is meant
        // to extend coverage, and any change to a day already shipped should have to be argued for
        // rather than slipping through because everything still parses.
        assertPublished("al-ain", LocalDate.of(2026, 8, 31), "04:39 05:53 12:20 15:46 18:40 19:55")
        assertPublished("al-ain", LocalDate.of(2026, 8, 15), "04:30 05:47 12:24 15:50 18:55 20:12")
        assertPublished("dubai", LocalDate.of(2026, 3, 1), "05:26 06:39 12:34 15:52 18:24 19:37")
    }

    private fun assertPublished(slug: String, date: LocalDate, expected: String) {
        val parsed = assertNotNull(
            OfficialTimetableParser.parse(File("src/main/assets/official/$slug.json").readText()),
        )
        val day = assertNotNull(parsed.firstOrNull { it.date == date }, "$slug has no $date")
        val actual = day.times.joinToString(" ") {
            it.adhan.atZone(ZoneId.of("Asia/Dubai")).toLocalTime().toString()
        }
        assertEquals(expected, actual, "$slug $date")
    }

    private companion object {
        /** A bundle worth shipping covers at least a year. */
        const val MINIMUM_BUNDLED_DAYS = 365
    }
}
