package com.salahwatch.core.calculation

import java.time.Duration
import java.time.Instant

/**
 * Turns a [PrayerCalendar] into the list of [PrayerPhase]s a complication should display.
 *
 * The output is deliberately *complete over a window*: every instant in `[from, until)` that the
 * calendar can speak for is covered by exactly one phase. The wear layer can then hand the whole
 * list to the platform as a `ComplicationDataTimeline` and stop thinking about time entirely — no
 * alarms, no polling, no wakeups between transitions.
 *
 * Phases never overlap, so the platform's "shortest entry wins" tie-break is never exercised, and
 * the resulting behaviour is exactly what the phase list says.
 *
 * Sunrise is not among them. It is in the schedule and has its own place in the app, but it is not a
 * prayer: nothing counts down to it, and no cycle stops at it.
 */
public class PhaseTimelineBuilder(
    private val tuning: PhaseTuning = PhaseTuning(),
) {
    /**
     * Builds the phase list for [mode] covering `[from, until)`.
     *
     * Phases are clipped to the window, so a phase already in progress at [from] is emitted with its
     * true content but a truncated start. Returns an empty list when the calendar has nothing to say
     * about the window, which the caller must render as "no data" rather than as a blank countdown.
     */
    public fun build(
        calendar: PrayerCalendar,
        mode: ComplicationMode,
        from: Instant,
        until: Instant,
    ): List<PrayerPhase> {
        require(until > from) { "Window must have positive duration" }
        if (calendar.isEmpty) return emptyList()

        // A prayer's "run" is the span it owns: from where the previous prayer's run ended, to
        // where its own does. Selection must be done on runs, not on Adhan instants — the phase
        // that displays a prayer's upcoming time *begins at the previous Adhan*, so a prayer whose
        // Adhan falls after the window can still own a phase inside it.
        val prayers = calendar.occurrences.filter { it.prayer.isPrayer }
        val runs = prayers.mapIndexed { index, occurrence ->
            val runStart = prayers.getOrNull(index - 1)
                ?.let { endOfRun(it, mode) }
                ?: calendar.coverageStart
                ?: from
            PhaseRun(occurrence, runStart, endOfRun(occurrence, mode))
        }

        return runs
            .filter { it.end > from && it.start < until }
            .flatMap { phasesFor(it.occurrence, mode, it.start) }
            .mapNotNull { it.clipTo(from, until) }
            .sortedBy { it.start }
    }

    /** A prayer together with the span of time it owns under a given mode. */
    private data class PhaseRun(
        val occurrence: PrayerOccurrence,
        val start: Instant,
        val end: Instant,
    )

    /** The phases belonging to a single prayer, spanning `[runStart, endOfRun)`. */
    private fun phasesFor(
        occurrence: PrayerOccurrence,
        mode: ComplicationMode,
        runStart: Instant,
    ): List<PrayerPhase> {
        val run = PrayerRunPhases(
            occurrence = occurrence,
            runStart = runStart,
            lead = tuning.leadFor(mode)?.let { Duration.ofMinutes(it) },
            postIqama = postIqama,
        )

        return when (mode) {
            ComplicationMode.PRAY_TIME -> run.adhanTimeOnly()
            ComplicationMode.ADHAN_COUNTDOWN -> run.adhanCountdownOnly()
            ComplicationMode.ADHAN_TIME_THEN_COUNTDOWN -> run.timeThenCountdown()
            ComplicationMode.ADHAN_THEN_IQAMA_COUNTDOWN -> run.countdownThenIqama()
            ComplicationMode.PRAYER_PROGRESS -> run.fullCycle()
        }
    }

    /** Where a prayer's phase run ends, which is where the following prayer's run begins. */
    private fun endOfRun(occurrence: PrayerOccurrence, mode: ComplicationMode): Instant =
        when (mode) {
            ComplicationMode.PRAY_TIME,
            ComplicationMode.ADHAN_COUNTDOWN,
            ComplicationMode.ADHAN_TIME_THEN_COUNTDOWN,
            -> occurrence.adhan

            ComplicationMode.ADHAN_THEN_IQAMA_COUNTDOWN -> occurrence.iqama ?: occurrence.adhan

            ComplicationMode.PRAYER_PROGRESS ->
                occurrence.iqama?.plus(postIqama) ?: occurrence.adhan
        }

    private val postIqama: Duration get() = Duration.ofMinutes(tuning.postIqamaMinutes)
}

/**
 * The phases one prayer can contribute, as an ordered set of boundaries within the span it owns.
 *
 * Nothing here checks whether a boundary makes sense, because it does not need to: a phase with no
 * room collapses to zero length and is dropped. That is what makes a long countdown lead safe.
 * Mode (c) counts down from 59 minutes out, which is longer than some gaps between prayers, and
 * where it is, the clock-time phase simply has nowhere to go and the run opens already counting
 * down — with no gap in coverage, because the countdown starts at [runStart] instead.
 */
private class PrayerRunPhases(
    private val occurrence: PrayerOccurrence,
    private val runStart: Instant,
    private val lead: Duration?,
    private val postIqama: Duration,
) {
    private val adhan: Instant = occurrence.adhan
    private val iqama: Instant? = occurrence.iqama

    /** Where a clock time gives way to a countdown, clamped so it can never precede the run. */
    private val countdownStart: Instant =
        lead?.let { maxOf(runStart, adhan.minus(it)) } ?: runStart

    private val adhanTime = PhaseContent.AbsoluteTime(adhan, TimeKind.ADHAN)
    private val adhanCountdown = PhaseContent.CountDown(adhan, TimeKind.ADHAN)

    fun adhanTimeOnly(): List<PrayerPhase> = listOfNotNull(phase(runStart, adhan, adhanTime))

    fun adhanCountdownOnly(): List<PrayerPhase> =
        listOfNotNull(phase(runStart, adhan, adhanCountdown))

    fun timeThenCountdown(): List<PrayerPhase> = listOfNotNull(
        phase(runStart, countdownStart, adhanTime),
        phase(countdownStart, adhan, adhanCountdown),
    )

    fun countdownThenIqama(): List<PrayerPhase> = listOfNotNull(
        phase(runStart, adhan, adhanCountdown),
        iqamaCountdown(),
    )

    fun fullCycle(): List<PrayerPhase> = listOfNotNull(
        phase(runStart, countdownStart, adhanTime),
        phase(countdownStart, adhan, adhanCountdown),
        iqamaCountdown(),
        postIqamaCountUp(),
    )

    private fun iqamaCountdown(): PrayerPhase? = iqama?.let {
        phase(adhan, it, PhaseContent.CountDown(it, TimeKind.IQAMA), adhan, it)
    }

    private fun postIqamaCountUp(): PrayerPhase? = iqama?.let {
        val until = it.plus(postIqama)
        phase(it, until, PhaseContent.CountUp(it, TimeKind.IQAMA), it, until)
    }

    /**
     * One phase, or null if it would be empty.
     *
     * The arc bounds default to the whole wait for the Adhan rather than to this phase's own
     * bounds, so a gauge sweeps once across the segment instead of restarting at every boundary
     * inside it.
     */
    private fun phase(
        start: Instant,
        end: Instant,
        content: PhaseContent,
        arcFrom: Instant = runStart,
        arcTo: Instant = adhan,
    ): PrayerPhase? = if (end > start && arcTo > arcFrom) {
        PrayerPhase(start, end, occurrence.prayer, content, occurrence.source, arcFrom, arcTo)
    } else {
        null
    }
}

/** Restricts a phase to a window, or drops it if it falls entirely outside. */
private fun PrayerPhase.clipTo(from: Instant, until: Instant): PrayerPhase? {
    val clippedStart = maxOf(start, from)
    val clippedEnd = minOf(end, until)
    return if (clippedEnd > clippedStart) copy(start = clippedStart, end = clippedEnd) else null
}
