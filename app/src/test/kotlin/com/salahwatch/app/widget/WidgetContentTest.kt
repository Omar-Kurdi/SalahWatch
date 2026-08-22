package com.salahwatch.app.widget

import com.salahwatch.core.calculation.PrayerCalendar
import com.salahwatch.core.calculation.SecondsWindow
import com.salahwatch.core.calculation.TimeKind
import com.salahwatch.core.model.DaySchedule
import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.PrayerTime
import com.salahwatch.core.model.ScheduleSource
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The widget's behaviour, tested where it can actually be observed.
 *
 * There is no device or emulator available to this project, so these tests are the evidence that
 * the widget shows the right thing — not a substitute for a screenshot, but the part that a
 * screenshot would not have caught anyway: the transitions.
 */
class WidgetContentTest {

    private val zone: ZoneId = ZoneId.of("Asia/Dubai")
    private val day = LocalDate.of(2026, 8, 14)
    private val tomorrow = day.plusDays(1)

    // A plausible day: Iqama twenty minutes after each Adhan except Maghrib, which is eight —
    // short enough that it sits entirely inside the ten-minute Iqama seconds window.
    private val calendar = PrayerCalendar.of(listOf(schedule(day), schedule(tomorrow)))

    private fun schedule(date: LocalDate) = DaySchedule(
        date = date,
        zone = zone,
        source = ScheduleSource.ALADHAN,
        times = listOf(
            time(date, Prayer.FAJR, "04:35", 20),
            time(date, Prayer.SUNRISE, "05:55", null),
            time(date, Prayer.DHUHR, "12:24", 20),
            time(date, Prayer.ASR, "15:49", 20),
            time(date, Prayer.MAGHRIB, "18:48", 8),
            time(date, Prayer.ISHA, "20:18", 20),
        ),
    )

    private fun time(date: LocalDate, prayer: Prayer, at: String, iqamaAfter: Int?): PrayerTime {
        val adhan = at(date, at)
        return PrayerTime(
            prayer = prayer,
            adhan = adhan,
            iqama = iqamaAfter?.let { adhan.plus(Duration.ofMinutes(it.toLong())) },
        )
    }

    private fun at(date: LocalDate, time: String): Instant =
        date.atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    private fun contentAt(date: LocalDate, time: String) =
        widgetContentFor(calendar, at(date, time), date, locationLabel = "Dubai")

    @Test
    fun `shows all six entries including sunrise, in time order`() {
        val content = contentAt(day, "10:00")

        assertEquals(
            listOf(
                Prayer.FAJR,
                Prayer.SUNRISE,
                Prayer.DHUHR,
                Prayer.ASR,
                Prayer.MAGHRIB,
                Prayer.ISHA,
            ),
            content.rows.map { it.prayer },
        )
        assertNull(
            content.rows.single { it.prayer == Prayer.SUNRISE }.iqama,
            "Sunrise has no congregation, so it must have no Iqama",
        )
    }

    @Test
    fun `counts down to the next Adhan, as a clock time while it is far off`() {
        val content = contentAt(day, "10:00")
        val headline = requireNotNull(content.headline)

        assertEquals(Prayer.DHUHR, headline.prayer)
        assertEquals(TimeKind.ADHAN, headline.kind)
        assertEquals(at(day, "12:24"), headline.target)
        assertEquals(CountdownStyle.CLOCK, headline.style)
    }

    @Test
    fun `starts ticking exactly fifteen minutes before the Adhan`() {
        assertEquals(
            CountdownStyle.CLOCK,
            requireNotNull(contentAt(day, "12:08").headline).style,
            "Sixteen minutes out is still outside the seconds window",
        )
        assertEquals(
            CountdownStyle.TICKING,
            requireNotNull(contentAt(day, "12:09").headline).style,
            "Fifteen minutes out is the boundary and counts as inside",
        )
    }

    @Test
    fun `after the Adhan it counts to that prayer's Iqama, not to the next prayer`() {
        // 12:30 is past Dhuhr's 12:24 Adhan but before its 12:44 Iqama.
        val headline = requireNotNull(contentAt(day, "12:30").headline)

        assertEquals(Prayer.DHUHR, headline.prayer, "The cycle must not skip to Asr")
        assertEquals(TimeKind.IQAMA, headline.kind)
        assertEquals(at(day, "12:44"), headline.target)
    }

    @Test
    fun `moves to the next prayer once the Iqama has passed`() {
        val headline = requireNotNull(contentAt(day, "12:45").headline)

        assertEquals(Prayer.ASR, headline.prayer)
        assertEquals(TimeKind.ADHAN, headline.kind)
        assertEquals(at(day, "15:49"), headline.target)
    }

    @Test
    fun `an Iqama shorter than its window ticks for its whole duration`() {
        // Maghrib's Iqama is eight minutes after the Adhan, inside the ten-minute window, so it
        // never gets a clock-time phase at all.
        val justAfterAdhan = requireNotNull(contentAt(day, "18:49").headline)

        assertEquals(TimeKind.IQAMA, justAfterAdhan.kind)
        assertEquals(
            CountdownStyle.TICKING,
            justAfterAdhan.style,
            "An Iqama below the ten-minute window shows seconds from the moment the Adhan is called",
        )
    }

    @Test
    fun `sunrise is never counted down to`() {
        // 05:00 sits between Fajr's Iqama and Sunrise. The next thing shown must be Dhuhr.
        val headline = requireNotNull(contentAt(day, "05:00").headline)

        assertEquals(Prayer.DHUHR, headline.prayer, "Sunrise is displayed but is not a prayer")
    }

    @Test
    fun `marks the prayer being counted to, and only that one`() {
        val content = contentAt(day, "12:30")

        assertEquals(
            listOf(Prayer.DHUHR),
            content.rows.filter { it.isNext }.map { it.prayer },
        )
    }

    @Test
    fun `does not mark today's Fajr while counting to tomorrow's`() {
        // Just after Isha's Iqama, the next prayer is Fajr — but tomorrow's. Today's Fajr is
        // sixteen hours past and must not be highlighted.
        val content = contentAt(day, "20:40")
        val headline = requireNotNull(content.headline)

        assertEquals(Prayer.FAJR, headline.prayer)
        assertEquals(at(tomorrow, "04:35"), headline.target)
        assertTrue(
            content.rows.none { it.isNext },
            "Tomorrow's Fajr is not on today's list, so no row should be marked",
        )
    }

    @Test
    fun `wakes at the window crossing when that comes first`() {
        val content = contentAt(day, "10:00")

        assertEquals(
            at(day, "12:24").minus(SecondsWindow.ADHAN),
            content.validUntil,
            "The next thing to happen is the countdown starting to tick",
        )
    }

    @Test
    fun `wakes at the Adhan itself once already ticking`() {
        val content = contentAt(day, "12:20")

        assertEquals(
            at(day, "12:24"),
            content.validUntil,
            "Inside the window the next change is the phase itself ending",
        )
    }

    @Test
    fun `reports empty rather than a zeroed countdown when nothing is cached`() {
        val content = widgetContentFor(
            calendar = PrayerCalendar.empty(),
            now = at(day, "10:00"),
            date = day,
            locationLabel = null,
        )

        assertTrue(content.isEmpty)
        assertNull(content.headline)
        assertNull(content.validUntil)
    }
}
