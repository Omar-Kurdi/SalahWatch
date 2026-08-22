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
    fun `after the last prayer, the Fajr row is marked and counts to tomorrow`() {
        // Just after Isha's Iqama the next prayer is Fajr, but tomorrow's — which appears at no
        // instant in today's list. Leaving nothing marked greys the entire widget out and shows a
        // finished day with nothing coming, which is what it did on the device.
        val content = contentAt(day, "20:40")
        val headline = requireNotNull(content.headline)

        assertEquals(Prayer.FAJR, headline.prayer)
        assertEquals(at(tomorrow, "04:35"), headline.target)

        val marked = content.rows.single { it.isNext }
        assertEquals(Prayer.FAJR, marked.prayer)
        assertEquals(
            at(tomorrow, "04:35"),
            requireNotNull(marked.countdown).target,
            "It must count to tomorrow's Fajr, not to today's which is sixteen hours past",
        )
    }

    @Test
    fun `only the prayer in play ticks, and only it shows seconds`() {
        val content = contentAt(day, "10:00")
        val live = content.rows.filter { it.countdown?.isLive == true }

        assertEquals(
            listOf(Prayer.DHUHR),
            live.map { it.prayer },
            "Exactly one prayer is ever close enough for seconds to mean anything",
        )
    }

    @Test
    fun `every prayer still ahead carries a countdown, and passed ones do not`() {
        val content = contentAt(day, "10:00")
        val withCountdown = content.rows.filter { it.countdown != null }.map { it.prayer }

        assertEquals(
            listOf(Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA),
            withCountdown,
            "Fajr has passed and Sunrise is not a prayer",
        )
        assertTrue(
            withCountdown.drop(1).all { prayer ->
                content.rows.single { it.prayer == prayer }.countdown?.isLive == false
            },
            "Only the prayer in play ticks",
        )
    }

    @Test
    fun `a later prayer counts to its own Adhan, not to the Iqama of the current one`() {
        // 12:30 is between Dhuhr's Adhan and its Iqama, so Dhuhr counts to the Iqama while Asr
        // must still be counting to its own Adhan.
        val content = contentAt(day, "12:30")

        val dhuhr = requireNotNull(content.rows.single { it.prayer == Prayer.DHUHR }.countdown)
        val asr = requireNotNull(content.rows.single { it.prayer == Prayer.ASR }.countdown)

        assertEquals(TimeKind.IQAMA, dhuhr.kind)
        assertEquals(at(day, "12:44"), dhuhr.target)
        assertEquals(TimeKind.ADHAN, asr.kind)
        assertEquals(at(day, "15:49"), asr.target)
    }

    @Test
    fun `a still countdown never displays a value that has gone stale`() {
        // The displayed figure and the redraw are derived from the same number, so whatever it
        // says is true for exactly as long as it is on screen.
        val target = at(day, "15:49")

        val far = requireNotNull(coarseCountdown(at(day, "10:26"), target))
        assertEquals(5L, far.amount)
        assertEquals(CoarseUnit.HOURS, far.unit)
        assertEquals(
            target.minus(Duration.ofHours(5)),
            far.changesAt,
            "It reads 5h until it is genuinely no longer five hours",
        )

        val near = requireNotNull(coarseCountdown(at(day, "15:16"), target))
        assertEquals(30L, near.amount)
        assertEquals(CoarseUnit.MINUTES, near.unit)
        assertEquals(target.minus(Duration.ofMinutes(30)), near.changesAt)
    }

    @Test
    fun `a still countdown rounds down, so it reads as a floor`() {
        val target = at(day, "15:49")
        val almostSix = requireNotNull(coarseCountdown(at(day, "09:52"), target))

        assertEquals(5L, almostSix.amount, "5h57m is 'at least five hours', not 'about six'")
    }

    @Test
    fun `the widget wakes when a still countdown would change, not only at phase boundaries`() {
        // At 10:00 the next phase boundary is hours away, but Asr's still countdown drops from 6h
        // to 5h at 09:49 + ... — well before it. Missing that leaves a stale figure on screen.
        val content = contentAt(day, "10:00")
        val validUntil = requireNotNull(content.validUntil)

        val nextStillChange = content.rows
            .mapNotNull { it.countdown }
            .filterNot { it.isLive }
            .mapNotNull { coarseCountdown(at(day, "10:00"), it.target)?.changesAt }
            .min()

        assertTrue(
            validUntil <= nextStillChange,
            "validUntil ($validUntil) must not overshoot the next still-countdown change " +
                "($nextStillChange)",
        )
    }

    @Test
    fun `wakes for whichever comes first — a phase, a window, or a still figure changing`() {
        // At 10:00 the countdown starts ticking at 12:09, but Isha's still figure drops from 10h
        // to 9h at 10:18. The earlier one wins, or "10h" sits on screen after it stopped being
        // true. Before still countdowns existed this test expected the 12:09 crossing.
        val content = contentAt(day, "10:00")

        assertEquals(at(day, "10:18"), content.validUntil)
        assertTrue(
            requireNotNull(content.validUntil) < at(day, "12:24").minus(SecondsWindow.ADHAN),
            "A still figure changes well before the seconds window opens",
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
