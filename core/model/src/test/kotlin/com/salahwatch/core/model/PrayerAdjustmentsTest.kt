package com.salahwatch.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Corrections, applied when times are read.
 *
 * They used to be applied when times were *fetched* — sent to AlAdhan as `tune`. That worked for
 * calculated times and did nothing at all for a mosque's published table, which is fixed and has no
 * parameter to tune. Moving them here is what makes a correction mean the same thing whichever
 * source the day came from.
 */
class PrayerAdjustmentsTest {

    private val dubai = ZoneId.of("Asia/Dubai")
    private val date = LocalDate.of(2026, 8, 15)

    private fun at(time: String): Instant =
        date.atTime(LocalTime.parse(time)).atZone(dubai).toInstant()

    private fun schedule(
        fajr: String = "04:30",
        sunrise: String = "05:52",
        dhuhr: String = "12:25",
        asr: String = "15:49",
        maghrib: String = "18:55",
        isha: String = "20:13",
    ) = DaySchedule(
        date = date,
        zone = dubai,
        times = listOf(
            PrayerTime(Prayer.FAJR, at(fajr), null),
            PrayerTime(Prayer.SUNRISE, at(sunrise), null),
            PrayerTime(Prayer.DHUHR, at(dhuhr), null),
            PrayerTime(Prayer.ASR, at(asr), null),
            PrayerTime(Prayer.MAGHRIB, at(maghrib), null),
            PrayerTime(Prayer.ISHA, at(isha), null),
        ),
        source = ScheduleSource.ALADHAN,
    )

    private fun DaySchedule.localTime(prayer: Prayer): String? =
        this[prayer]?.adhan?.atZone(dubai)?.toLocalTime()?.toString()

    @Test
    fun `shifts each prayer by its own correction`() {
        val shifted = schedule().shiftedBy(PrayerAdjustments(sunrise = -4, asr = 1))

        assertEquals("05:48", shifted.localTime(Prayer.SUNRISE))
        assertEquals("15:50", shifted.localTime(Prayer.ASR))
        assertEquals("04:30", shifted.localTime(Prayer.FAJR), "Untouched prayers must not move")
    }

    @Test
    fun `returns the same schedule untouched when nothing is corrected`() {
        val original = schedule()

        // Not merely equal: the common case must not rebuild the day on every read.
        assertSame(original, original.shiftedBy(PrayerAdjustments()))
    }

    @Test
    fun `clears Iqama, which is recomputed from the corrected Adhan`() {
        val withIqama = schedule().copy(
            times = schedule().times.map {
                if (it.prayer.isPrayer) it.copy(iqama = it.adhan.plusSeconds(1200)) else it
            },
        )

        val shifted = withIqama.shiftedBy(PrayerAdjustments(fajr = 3))

        assertNull(shifted[Prayer.FAJR]?.iqama, "A stale congregation time is worse than none")
    }

    @Test
    fun `a correction moves by a delta, so rapid taps all count`() {
        // The screen's displayed value arrives via a write to disk and back. Computing a new total
        // from it lost every tap that landed before the round trip — five taps moved it by one.
        var adjustments = PrayerAdjustments()
        repeat(5) { adjustments = adjustments.movedBy(Prayer.ASR, 1) }

        assertEquals(5, adjustments[Prayer.ASR])
        assertEquals(4, adjustments.movedBy(Prayer.ASR, -1)[Prayer.ASR], "and it steps back down")
    }

    @Test
    fun `a correction cannot be pushed beyond what a correction is for`() {
        // A held finger should not be able to reorder the day into a state that takes as many taps
        // to escape as it took to reach.
        var adjustments = PrayerAdjustments()
        repeat(PrayerAdjustments.MAX_MINUTES + 50) { adjustments = adjustments.movedBy(Prayer.FAJR, 1) }
        assertEquals(PrayerAdjustments.MAX_MINUTES, adjustments[Prayer.FAJR])

        repeat(PrayerAdjustments.MAX_MINUTES * 3) { adjustments = adjustments.movedBy(Prayer.FAJR, -1) }
        assertEquals(-PrayerAdjustments.MAX_MINUTES, adjustments[Prayer.FAJR])
    }

    @Test
    fun `moving one prayer leaves the others alone`() {
        val moved = PrayerAdjustments(dhuhr = 3).movedBy(Prayer.ASR, 2)

        assertEquals(2, moved[Prayer.ASR])
        assertEquals(3, moved[Prayer.DHUHR])
        assertEquals(0, moved[Prayer.FAJR])
    }

    @Test
    fun `keeps the day in order when a correction would otherwise reverse two prayers`() {
        // Sunrise is 82 minutes after Fajr here; pushing Fajr past it must not leave a schedule
        // that violates DaySchedule's own chronological invariant.
        val shifted = schedule().shiftedBy(PrayerAdjustments(fajr = 90))

        assertEquals(
            listOf(Prayer.SUNRISE, Prayer.FAJR),
            shifted.times.take(2).map { it.prayer },
        )
    }
}
