package com.salahwatch.wear.complications

import com.salahwatch.core.calculation.PhaseContent
import com.salahwatch.core.calculation.PrayerPhase
import com.salahwatch.core.calculation.TimeKind
import java.time.Duration

/** A phase together with how precisely its value should be rendered. */
internal data class RenderedPhase(
    val phase: PrayerPhase,
    val granularity: ComplicationTexts.Granularity,
)

/**
 * Splits countdown phases so that seconds appear only when they are useful.
 *
 * A countdown that ticks by the second all afternoon would make the watch face redraw every second
 * for hours — the exact cost this app is built to avoid — while telling you nothing you could not
 * read from "5h 30m". In the last few minutes before the Adhan the opposite is true: seconds are the
 * whole point.
 *
 * The timeline makes this free. Rather than choosing one granularity and living with it, a countdown
 * phase is cut at its threshold and published as two entries: minutes up to that point, seconds
 * after it. The platform switches between them on its own.
 *
 * The two thresholds differ because the two waits differ. A wait for an Adhan is long, and the last
 * quarter of an hour is when it starts to matter. A wait for an Iqama is short to begin with, so its
 * window is shorter — and an Iqama offset below that window has no minutes phase at all: it takes
 * the seconds branch whole and reads as `mm:ss` from the moment the Adhan is called.
 *
 * The count-up after an Iqama is never split: it runs for a few minutes and is seconds all the way.
 */
internal fun List<PrayerPhase>.withCountdownGranularity(
    adhanThreshold: Duration = ADHAN_SECONDS_THRESHOLD,
    iqamaThreshold: Duration = IQAMA_SECONDS_THRESHOLD,
): List<RenderedPhase> = flatMap { phase ->
    val content = phase.content

    if (content is PhaseContent.CountUp) {
        return@flatMap listOf(RenderedPhase(phase, ComplicationTexts.Granularity.SECONDS))
    }
    if (content !is PhaseContent.CountDown) {
        return@flatMap listOf(RenderedPhase(phase, ComplicationTexts.Granularity.MINUTES))
    }

    val threshold = when (content.kind) {
        TimeKind.ADHAN -> adhanThreshold
        TimeKind.IQAMA -> iqamaThreshold
    }
    val switchAt = content.target.minus(threshold)

    when {
        // Entirely inside the final minutes.
        !switchAt.isAfter(phase.start) ->
            listOf(RenderedPhase(phase, ComplicationTexts.Granularity.SECONDS))

        // Ends before the final minutes begin.
        !switchAt.isBefore(phase.end) ->
            listOf(RenderedPhase(phase, ComplicationTexts.Granularity.MINUTES))

        // Straddles the boundary, so it becomes two entries. Both keep the original arc bounds, so
        // the gauge sweeps once across the whole wait rather than emptying itself at the switch.
        else -> listOf(
            RenderedPhase(
                phase.copy(end = switchAt),
                ComplicationTexts.Granularity.MINUTES,
            ),
            RenderedPhase(
                phase.copy(start = switchAt),
                ComplicationTexts.Granularity.SECONDS,
            ),
        )
    }
}

/** When a countdown to an Adhan starts showing seconds. */
internal val ADHAN_SECONDS_THRESHOLD: Duration = Duration.ofMinutes(ADHAN_SECONDS_MINUTES)

/**
 * When a countdown to an Iqama starts showing seconds.
 *
 * Shorter than the Adhan's, because the whole wait is shorter. An Iqama offset at or below this —
 * the SPEC default for Maghrib is eight minutes — never reaches a minutes phase and shows `mm:ss`
 * for its entire duration, which is the intent rather than an edge case.
 */
internal val IQAMA_SECONDS_THRESHOLD: Duration = Duration.ofMinutes(IQAMA_SECONDS_MINUTES)

private const val ADHAN_SECONDS_MINUTES = 15L
private const val IQAMA_SECONDS_MINUTES = 10L
