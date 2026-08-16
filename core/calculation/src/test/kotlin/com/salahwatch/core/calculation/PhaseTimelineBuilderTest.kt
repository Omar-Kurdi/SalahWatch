package com.salahwatch.core.calculation

import com.salahwatch.core.model.Prayer
import java.time.Duration
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhaseTimelineBuilderTest {

    private val builder = PhaseTimelineBuilder()
    private val day1 = LocalDate.of(2026, 8, 14)
    private val day2 = LocalDate.of(2026, 8, 15)
    private val calendar = PrayerCalendar.of(listOf(daySchedule(day1), daySchedule(day2)))

    private fun phases(
        mode: ComplicationMode,
        fromTime: String = "12:30",
        untilTime: String = "23:00",
    ) = builder.build(
        calendar = calendar,
        mode = mode,
        from = at(day1, DUBAI, fromTime),
        until = at(day1, DUBAI, untilTime),
    )

    @Test
    fun `phases never overlap and leave no gaps`() {
        for (mode in ComplicationMode.entries) {
            val result = phases(mode)
            assertTrue(result.isNotEmpty(), "$mode produced no phases")
            result.zipWithNext { a, b ->
                assertEquals(a.end, b.start, "$mode has a discontinuity between $a and $b")
            }
        }
    }

    @Test
    fun `full cycle walks Adhan time, Adhan countdown, Iqama countdown, then elapsed`() {
        // Asr is at 15:49 with Iqama at 16:09. The window opens before the 59-minute countdown lead
        // so that all four phases of the cycle are visible in one list.
        val result = builder.build(
            calendar = calendar,
            mode = ComplicationMode.PRAYER_PROGRESS,
            from = at(day1, DUBAI, "14:00"),
            until = at(day1, DUBAI, "16:20"),
        )

        val asr = at(day1, DUBAI, "15:49")
        val iqama = at(day1, DUBAI, "16:09")

        assertEquals(
            listOf(
                PhaseContent.AbsoluteTime(asr, TimeKind.ADHAN),
                PhaseContent.CountDown(asr, TimeKind.ADHAN),
                PhaseContent.CountDown(iqama, TimeKind.IQAMA),
                PhaseContent.CountUp(iqama, TimeKind.IQAMA),
            ),
            result.take(4).map { it.content },
            "Asr's four phases must appear in SPEC's stated order",
        )
        assertEquals(List(4) { Prayer.ASR }, result.take(4).map { it.prayer })

        // Mode (c) starts counting down 59 minutes out, unlike mode (b)'s ten.
        assertEquals(at(day1, DUBAI, "14:50"), result[1].start)
        // Elapsed-time display persists 5 minutes past the Iqama, then the cycle restarts.
        assertEquals(at(day1, DUBAI, "16:14"), result[3].end)

        // The window runs to 16:20, so the remaining 6 minutes belong to Maghrib — the cycle
        // returning to "next Adhan's clock time" is the final step of SPEC mode (c).
        val handover = result[4]
        assertEquals(Prayer.MAGHRIB, handover.prayer)
        assertEquals(at(day1, DUBAI, "16:14"), handover.start)
        assertEquals(
            PhaseContent.AbsoluteTime(at(day1, DUBAI, "18:55"), TimeKind.ADHAN),
            handover.content,
        )
    }

    @Test
    fun `adhan time only mode shows one clock time per prayer`() {
        val result = phases(ComplicationMode.PRAY_TIME, fromTime = "12:30", untilTime = "19:30")

        assertTrue(result.all { it.content is PhaseContent.AbsoluteTime })
        assertEquals(
            listOf(Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA),
            result.map { it.prayer },
        )
    }

    @Test
    fun `countdown mode counts down to each Adhan`() {
        val result = phases(ComplicationMode.ADHAN_COUNTDOWN, fromTime = "12:30", untilTime = "19:30")

        assertTrue(result.all { it.content is PhaseContent.CountDown })
        assertEquals(
            listOf(
                at(day1, DUBAI, "15:49"),
                at(day1, DUBAI, "18:55"),
                at(day1, DUBAI, "20:13"),
            ),
            result.map { (it.content as PhaseContent.CountDown).target },
        )
    }

    @Test
    fun `countdown then Iqama alternates between the two targets`() {
        val result = builder.build(
            calendar = calendar,
            mode = ComplicationMode.ADHAN_THEN_IQAMA_COUNTDOWN,
            from = at(day1, DUBAI, "15:00"),
            until = at(day1, DUBAI, "16:30"),
        )

        assertEquals(
            listOf(TimeKind.ADHAN, TimeKind.IQAMA, TimeKind.ADHAN),
            result.map { it.content.kind },
        )
        assertEquals(Prayer.ASR, result[0].prayer)
        assertEquals(Prayer.ASR, result[1].prayer)
        assertEquals(Prayer.MAGHRIB, result[2].prayer, "After Asr's Iqama the run moves to Maghrib")
    }

    @Test
    fun `a phase already in progress at the window start is clipped, not dropped`() {
        // 15:45 is inside Asr's countdown window, which began at 15:39.
        val from = at(day1, DUBAI, "15:45")
        val result = builder.build(
            calendar = calendar,
            mode = ComplicationMode.PRAYER_PROGRESS,
            from = from,
            until = at(day1, DUBAI, "16:00"),
        )

        assertEquals(from, result.first().start)
        assertEquals(
            PhaseContent.CountDown(at(day1, DUBAI, "15:49"), TimeKind.ADHAN),
            result.first().content,
            "The in-progress countdown must survive clipping with its target intact",
        )
    }

    @Test
    fun `phases stay within the requested window`() {
        val from = at(day1, DUBAI, "12:30")
        val until = at(day1, DUBAI, "23:00")
        for (mode in ComplicationMode.entries) {
            for (phase in builder.build(calendar, mode, from, until)) {
                assertTrue(phase.start >= from, "$mode leaked before the window: $phase")
                assertTrue(phase.end <= until, "$mode leaked past the window: $phase")
            }
        }
    }

    @Test
    fun `timeline spans midnight into the next day`() {
        val result = builder.build(
            calendar = calendar,
            mode = ComplicationMode.PRAY_TIME,
            from = at(day1, DUBAI, "21:00"),
            until = at(day2, DUBAI, "06:00"),
        )

        assertTrue(result.isNotEmpty(), "Rollover past Isha must still produce phases")
        assertEquals(Prayer.FAJR, result.first().prayer)
        assertEquals(day2, result.first().let { calendar.nextPrayer(it.start)?.date })
    }

    @Test
    fun `custom tuning moves the countdown boundary`() {
        val custom = PhaseTimelineBuilder(PhaseTuning(progressLeadMinutes = 30, postIqamaMinutes = 2))
        val result = custom.build(
            calendar = calendar,
            mode = ComplicationMode.PRAYER_PROGRESS,
            from = at(day1, DUBAI, "15:00"),
            until = at(day1, DUBAI, "16:20"),
        )

        assertEquals(at(day1, DUBAI, "15:19"), result[1].start, "30-minute lead before 15:49 Asr")
        assertEquals(at(day1, DUBAI, "16:11"), result[3].end, "2 minutes past the 16:09 Iqama")
    }

    @Test
    fun `mode b keeps its own shorter lead`() {
        // (b) and (c) must not share a lead: (c) counts down for the last hour, (b) for the last
        // half hour. Thirty rather than the ten first specified — ten was inside the fifteen-minute
        // seconds window, so mode (b) would have jumped from a clock time straight to mm:ss.
        val result = builder.build(
            calendar = calendar,
            mode = ComplicationMode.ADHAN_TIME_THEN_COUNTDOWN,
            from = at(day1, DUBAI, "15:00"),
            until = at(day1, DUBAI, "15:49"),
        )

        assertEquals(at(day1, DUBAI, "15:19"), result[1].start, "30-minute lead before 15:49 Asr")
    }

    @Test
    fun `a lead longer than the gap opens the run already counting down`() {
        // Asr's run begins at Dhuhr's Iqama plus five (12:50) and Asr is at 15:49, so a three-hour
        // lead leaves the clock-time phase no room at all. The run must still be covered from its
        // very first instant — the countdown simply starts at the top of it instead. A gap here
        // would drop the watch face to the no-data placeholder between two prayers.
        val custom = PhaseTimelineBuilder(PhaseTuning(progressLeadMinutes = 180))
        val result = custom.build(
            calendar = calendar,
            mode = ComplicationMode.PRAYER_PROGRESS,
            from = at(day1, DUBAI, "12:50"),
            until = at(day1, DUBAI, "15:49"),
        )

        assertEquals(
            PhaseContent.CountDown(at(day1, DUBAI, "15:49"), TimeKind.ADHAN),
            result.first().content,
            "The collapsed clock-time phase must not leave the run starting on nothing",
        )
        assertEquals(at(day1, DUBAI, "12:50"), result.first().start)
        result.zipWithNext { a, b ->
            assertEquals(a.end, b.start, "Coverage must stay contiguous across $a and $b")
        }
    }

    @Test
    fun `a gauge measures the whole wait, not the phase it is drawn in`() {
        val result = builder.build(
            calendar = calendar,
            mode = ComplicationMode.PRAYER_PROGRESS,
            from = at(day1, DUBAI, "14:00"),
            until = at(day1, DUBAI, "16:20"),
        )

        // Asr's run opens at Dhuhr's Iqama plus five. Both the clock-time phase and the countdown
        // that follows it measure their arc across that whole span, so the ring sweeps once rather
        // than resetting when the display changes.
        val runStart = at(day1, DUBAI, "12:50")
        val asr = at(day1, DUBAI, "15:49")
        for (phase in result.take(2)) {
            assertEquals(runStart, phase.arcFrom, "Arc must start at the run, not the entry")
            assertEquals(asr, phase.arcTo)
        }

        // The Iqama countdown is its own segment: Adhan to Iqama.
        assertEquals(asr, result[2].arcFrom)
        assertEquals(at(day1, DUBAI, "16:09"), result[2].arcTo)
    }

    @Test
    fun `empty calendar yields no phases rather than an empty countdown`() {
        val result = PhaseTimelineBuilder().build(
            calendar = PrayerCalendar.empty(),
            mode = ComplicationMode.PRAYER_PROGRESS,
            from = at(day1, DUBAI, "12:00"),
            until = at(day1, DUBAI, "18:00"),
        )
        assertTrue(result.isEmpty())
    }

    // --- Daylight saving transitions -------------------------------------------------------
    //
    // Wall-clock times below are the real values returned by the AlAdhan API for London with
    // iso8601=true, verified on 2026-08-14. The offset flips from +01:00 to +00:00 overnight on
    // 25 October 2026.

    @Test
    fun `phases stay contiguous across the autumn DST transition`() {
        val oct24 = LocalDate.of(2026, 10, 24)
        val oct25 = LocalDate.of(2026, 10, 25)
        val london = PrayerCalendar.of(
            listOf(
                daySchedule(
                    oct24, zone = LONDON,
                    fajr = "05:48", sunrise = "07:29", dhuhr = "12:45",
                    asr = "15:07", maghrib = "17:49", isha = "19:34",
                ),
                daySchedule(
                    oct25, zone = LONDON,
                    fajr = "04:49", sunrise = "06:31", dhuhr = "11:45",
                    asr = "14:05", maghrib = "16:47", isha = "18:32",
                ),
            ),
        )

        val result = PhaseTimelineBuilder().build(
            calendar = london,
            mode = ComplicationMode.PRAYER_PROGRESS,
            from = at(oct24, LONDON, "19:00"),
            until = at(oct25, LONDON, "12:00"),
        )

        assertTrue(result.isNotEmpty())
        result.zipWithNext { a, b ->
            assertEquals(a.end, b.start, "DST transition introduced a discontinuity: $a -> $b")
        }
    }

    @Test
    fun `the night of the autumn transition is a real hour longer`() {
        val oct24 = LocalDate.of(2026, 10, 24)
        val oct25 = LocalDate.of(2026, 10, 25)

        // Isha 19:34 BST on the 24th, Fajr 04:49 GMT on the 25th.
        val isha = at(oct24, LONDON, "19:34")
        val fajr = at(oct25, LONDON, "04:49")
        val elapsed = Duration.between(isha, fajr)

        // Naive wall-clock subtraction would say 9h15m; absolute time correctly says 10h15m.
        assertEquals(Duration.ofHours(10).plusMinutes(15), elapsed)
    }

    @Test
    fun `phases stay contiguous across the spring DST transition`() {
        // 29 March 2026: London clocks jump 01:00 -> 02:00, so that night is an hour shorter.
        val mar28 = LocalDate.of(2026, 3, 28)
        val mar29 = LocalDate.of(2026, 3, 29)
        val london = PrayerCalendar.of(
            listOf(
                daySchedule(
                    mar28, zone = LONDON,
                    fajr = "04:11", sunrise = "05:58", dhuhr = "12:07",
                    asr = "15:35", maghrib = "18:17", isha = "19:57",
                ),
                daySchedule(
                    mar29, zone = LONDON,
                    fajr = "06:08", sunrise = "07:56", dhuhr = "13:07",
                    asr = "16:37", maghrib = "19:19", isha = "21:00",
                ),
            ),
        )

        val result = PhaseTimelineBuilder().build(
            calendar = london,
            mode = ComplicationMode.PRAYER_PROGRESS,
            from = at(mar28, LONDON, "20:00"),
            until = at(mar29, LONDON, "14:00"),
        )

        assertTrue(result.isNotEmpty())
        result.zipWithNext { a, b ->
            assertEquals(a.end, b.start, "Spring DST introduced a discontinuity: $a -> $b")
        }
        assertTrue(
            result.all { it.end > it.start },
            "The lost hour must not produce a zero or negative duration phase",
        )
    }
}
