package com.salahwatch.wear.complications

import com.salahwatch.core.calculation.PhaseContent
import com.salahwatch.core.calculation.PrayerPhase
import com.salahwatch.core.calculation.TimeKind
import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.ScheduleSource
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderedPhaseTest {

    private val base: Instant = Instant.parse("2026-08-15T12:00:00Z")

    private fun countdown(startMinutes: Long, endMinutes: Long, targetMinutes: Long) = PrayerPhase(
        start = base.plus(Duration.ofMinutes(startMinutes)),
        end = base.plus(Duration.ofMinutes(endMinutes)),
        prayer = Prayer.ASR,
        content = PhaseContent.CountDown(base.plus(Duration.ofMinutes(targetMinutes)), TimeKind.ADHAN),
        source = ScheduleSource.ALADHAN,
    )

    @Test
    fun `a long countdown splits at fifteen minutes out`() {
        // Runs from an hour out down to the target.
        val result = listOf(countdown(0, 60, 60)).withCountdownGranularity()

        assertEquals(2, result.size)
        assertEquals(ComplicationTexts.Granularity.MINUTES, result[0].granularity)
        assertEquals(ComplicationTexts.Granularity.SECONDS, result[1].granularity)
        assertEquals(base.plus(Duration.ofMinutes(45)), result[0].phase.end)
        assertEquals(base.plus(Duration.ofMinutes(45)), result[1].phase.start)
    }

    @Test
    fun `the split leaves no gap or overlap`() {
        val result = listOf(countdown(0, 60, 60)).withCountdownGranularity()
        assertEquals(
            result[0].phase.end,
            result[1].phase.start,
            "A gap here would drop the complication to its no-data placeholder mid-countdown",
        )
    }

    @Test
    fun `a countdown entirely inside the final minutes shows seconds throughout`() {
        val result = listOf(countdown(50, 60, 60)).withCountdownGranularity()

        assertEquals(1, result.size)
        assertEquals(ComplicationTexts.Granularity.SECONDS, result.single().granularity)
    }

    @Test
    fun `a countdown that ends before the final minutes never shows seconds`() {
        // Ends 30 minutes out — the following phase takes over well before the threshold.
        val result = listOf(countdown(0, 30, 60)).withCountdownGranularity()

        assertEquals(1, result.size)
        assertEquals(ComplicationTexts.Granularity.MINUTES, result.single().granularity)
    }

    @Test
    fun `a clock time is left alone`() {
        val clock = PrayerPhase(
            start = base,
            end = base.plus(Duration.ofHours(1)),
            prayer = Prayer.ASR,
            content = PhaseContent.AbsoluteTime(base.plus(Duration.ofHours(3)), TimeKind.ADHAN),
            source = ScheduleSource.ALADHAN,
        )

        val result = listOf(clock).withCountdownGranularity()

        assertEquals(1, result.size)
        assertEquals(ComplicationTexts.Granularity.MINUTES, result.single().granularity)
    }

    @Test
    fun `the minutes after the Iqama count up in seconds`() {
        // "Pray 3:12". This runs for five minutes, so minutes alone would sit on "3m" for a minute
        // at a time and read as a frozen complication rather than a running one.
        val elapsed = PrayerPhase(
            start = base,
            end = base.plus(Duration.ofMinutes(5)),
            prayer = Prayer.ASR,
            content = PhaseContent.CountUp(base, TimeKind.IQAMA),
            source = ScheduleSource.ALADHAN,
        )

        val result = listOf(elapsed).withCountdownGranularity()

        assertEquals(1, result.size, "A count-up is never split")
        assertEquals(ComplicationTexts.Granularity.SECONDS, result.single().granularity)
    }

    @Test
    fun `a split countdown keeps one arc across both halves`() {
        // The gauge measures the whole wait. If the split reset it to the entry's own bounds, the
        // ring would empty itself and start again at the moment the text switched to seconds.
        val result = listOf(countdown(0, 60, 60)).withCountdownGranularity()

        assertEquals(2, result.size)
        assertEquals(base, result[0].phase.arcFrom)
        assertEquals(base, result[1].phase.arcFrom)
        assertTrue(result.all { it.phase.arcTo == base.plus(Duration.ofMinutes(60)) })
    }

    @Test
    fun `phases never gain zero or negative duration`() {
        // A phase ending exactly on the threshold must not produce an empty second half.
        val result = listOf(countdown(0, 45, 60)).withCountdownGranularity()

        assertTrue(
            result.all { it.phase.end > it.phase.start },
            "PrayerPhase rejects non-positive durations, so an empty split would throw at build time",
        )
    }

    @Test
    fun `the threshold is configurable`() {
        val result = listOf(countdown(0, 60, 60))
            .withCountdownGranularity(adhanThreshold = Duration.ofMinutes(10))

        assertEquals(base.plus(Duration.ofMinutes(50)), result[0].phase.end)
    }

    @Test
    fun `an Iqama countdown switches to seconds later than an Adhan one`() {
        // Ten minutes rather than fifteen: the whole wait for an Iqama is short to begin with.
        val iqama = PrayerPhase(
            start = base,
            end = base.plus(Duration.ofMinutes(20)),
            prayer = Prayer.ASR,
            content = PhaseContent.CountDown(base.plus(Duration.ofMinutes(20)), TimeKind.IQAMA),
            source = ScheduleSource.ALADHAN,
        )

        val result = listOf(iqama).withCountdownGranularity()

        assertEquals(2, result.size)
        assertEquals(base.plus(Duration.ofMinutes(10)), result[0].phase.end)
        assertEquals(ComplicationTexts.Granularity.SECONDS, result[1].granularity)
    }

    @Test
    fun `a short Iqama offset shows seconds for its whole duration`() {
        // SPEC's Maghrib default is eight minutes, inside the ten-minute window, so it never has a
        // minutes phase — it reads as mm:ss from the moment the Adhan is called.
        val iqama = PrayerPhase(
            start = base,
            end = base.plus(Duration.ofMinutes(8)),
            prayer = Prayer.MAGHRIB,
            content = PhaseContent.CountDown(base.plus(Duration.ofMinutes(8)), TimeKind.IQAMA),
            source = ScheduleSource.ALADHAN,
        )

        val result = listOf(iqama).withCountdownGranularity()

        assertEquals(1, result.size)
        assertEquals(ComplicationTexts.Granularity.SECONDS, result.single().granularity)
    }
}
