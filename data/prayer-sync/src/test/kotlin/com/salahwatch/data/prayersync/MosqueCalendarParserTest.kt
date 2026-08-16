package com.salahwatch.data.prayersync

import com.salahwatch.core.model.Prayer
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The safety net around mosque-entered data.
 *
 * Both hazards covered here were found in live Mawaqit data, not imagined: calendars that publish
 * seven times instead of six, and a mosque in Istanbul declaring `Asia/Jerusalem`. Either would
 * produce confidently wrong prayer times, which is the worst way for this app to fail.
 */
class MosqueCalendarParserTest {

    private val dubai: ZoneId = ZoneId.of("Asia/Dubai")
    private val date: LocalDate = LocalDate.of(2026, 8, 15)

    private fun sunriseAt(time: String): (LocalDate) -> Instant? =
        { d -> d.atTime(LocalTime.parse(time)).atZone(dubai).toInstant() }

    private fun parse(times: List<String>, sunrise: String = "05:52") =
        MosqueCalendarParser(sunriseAt(sunrise)).parse(mapOf(date to times), dubai)

    /** Real published values, taken from a mosque that matches its national timetable exactly. */
    private val sixTimes = listOf("04:30", "05:47", "12:24", "15:50", "18:54", "20:12")

    /** A real Istanbul mosque's seven-entry form, with an extra leading time. */
    private val sevenTimes = listOf("04:29", "05:07", "06:07", "13:14", "17:03", "20:11", "21:41")

    @Test
    fun `reads a six-time calendar`() {
        val result = assertIs<MosqueCalendarResult.Usable>(parse(sixTimes))
        val day = result.schedules.single()

        assertEquals(LocalTime.of(4, 30), day[Prayer.FAJR]?.adhan?.atZone(dubai)?.toLocalTime())
        assertEquals(LocalTime.of(5, 47), day[Prayer.SUNRISE]?.adhan?.atZone(dubai)?.toLocalTime())
        assertEquals(LocalTime.of(20, 12), day[Prayer.ISHA]?.adhan?.atZone(dubai)?.toLocalTime())
    }

    @Test
    fun `reads a seven-time calendar from the end, not the front`() {
        // Reading from the front would put 04:29 in Fajr and 05:07 in Sunrise, shifting every
        // prayer by one slot — the whole schedule wrong, and plausible enough to go unnoticed.
        val result = assertIs<MosqueCalendarResult.Usable>(parse(sevenTimes, sunrise = "06:14"))
        val day = result.schedules.single()

        assertEquals(LocalTime.of(5, 7), day[Prayer.FAJR]?.adhan?.atZone(dubai)?.toLocalTime())
        assertEquals(LocalTime.of(6, 7), day[Prayer.SUNRISE]?.adhan?.atZone(dubai)?.toLocalTime())
        assertEquals(LocalTime.of(21, 41), day[Prayer.ISHA]?.adhan?.atZone(dubai)?.toLocalTime())
    }

    @Test
    fun `rejects a calendar whose sunrise is hours out`() {
        // A mosque that has entered the wrong timezone. Rejecting is right: the app falls back to
        // calculated times rather than showing a schedule that is confidently wrong.
        val result = parse(sixTimes, sunrise = "08:52")
        val rejected = assertIs<MosqueCalendarResult.Rejected>(result)
        assertIs<MosqueCalendarProblem.SunriseMismatch>(rejected.problem)
    }

    @Test
    fun `allows the genuine spread between conventions`() {
        // Mosques legitimately differ from the astronomical value by tens of minutes depending on
        // the horizon and convention they use. That must not be mistaken for broken data.
        assertIs<MosqueCalendarResult.Usable>(parse(sixTimes, sunrise = "06:15"))
    }

    @Test
    fun `rejects a short calendar rather than guessing`() {
        val result = parse(listOf("04:30", "05:47", "12:24"))
        val rejected = assertIs<MosqueCalendarResult.Rejected>(result)
        assertIs<MosqueCalendarProblem.TooFewTimes>(rejected.problem)
    }

    @Test
    fun `rejects unparseable times`() {
        val result = parse(listOf("04:30", "05:47", "--:--", "15:50", "18:54", "20:12"))
        val rejected = assertIs<MosqueCalendarResult.Rejected>(result)
        assertIs<MosqueCalendarProblem.Unparseable>(rejected.problem)
    }

    @Test
    fun `never invents an Iqama`() {
        // Iqama comes from settings or from the mosque's own offsets, applied later. Baking one in
        // here would create a second source of truth.
        val result = assertIs<MosqueCalendarResult.Usable>(parse(sixTimes))
        assertTrue(result.schedules.single().times.all { it.iqama == null })
    }

    @Test
    fun `skips validation when no reference sunrise is available`() {
        // Offline, or beyond the calculator's range: better to accept the mosque's own data than to
        // reject everything and show nothing.
        val parser = MosqueCalendarParser { null }
        assertIs<MosqueCalendarResult.Usable>(parser.parse(mapOf(date to sixTimes), dubai))
    }

    @Test
    fun `times land on the right day in the mosque's zone`() {
        val result = assertIs<MosqueCalendarResult.Usable>(parse(sixTimes))
        val fajr = result.schedules.single()[Prayer.FAJR]!!.adhan

        assertEquals(date, fajr.atZone(dubai).toLocalDate())
        // Dubai is UTC+4, so 04:30 local is the previous day in UTC — the kind of thing that breaks
        // when times are stored as wall clocks instead of instants.
        assertEquals(Duration.ofHours(4), Duration.ofSeconds(dubai.rules.getOffset(fajr).totalSeconds.toLong()))
    }
}
