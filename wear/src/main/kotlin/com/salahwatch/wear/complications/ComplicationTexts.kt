package com.salahwatch.wear.complications

import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.CountUpTimeReference
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import com.salahwatch.core.calculation.PhaseContent
import com.salahwatch.core.calculation.TimeKind
import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.ScheduleSource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * The text primitives every complication is assembled from.
 *
 * The governing rule: **never render a changing value ourselves.** Anything that varies with time is
 * handed to the platform as a [TimeDifferenceComplicationText] so the watch face re-renders it
 * without waking this process. A countdown built from a pre-formatted string would freeze until the
 * next update, and the minimum update period is five minutes.
 */
internal object ComplicationTexts {

    /**
     * A fixed wall-clock time, e.g. "4:32".
     *
     * Plain text, deliberately. `TimeFormatComplicationText` formats *the current time*, which is
     * what a clock complication wants — not what this needs. The next Adhan is a fixed instant whose
     * rendering does not change while it is still the next Adhan, and the moment it stops being so,
     * the timeline has already moved to the following entry. A live formatter here would show the
     * current time under a prayer's name, which is simply wrong.
     */
    fun clock(instant: Instant, zone: ZoneId): ComplicationText =
        PlainComplicationText.Builder(format(instant, zone)).build()

    /**
     * A live countdown to [target].
     *
     * Two styles, chosen by how close the target is, because one style cannot do both jobs:
     *
     * - **[TimeDifferenceStyle.SHORT_DUAL_UNIT]** for anything further out: "5h 30m" rather than the
     *   "6h" a single-unit style would round to. Under an hour it shows plain minutes, and it never
     *   shows seconds — which is right for a value you glance at.
     * - **[TimeDifferenceStyle.STOPWATCH]** for the final minutes: under an hour it renders as
     *   `04:59`, ticking down by the second, which is what you want when the Adhan is imminent.
     *
     * Both stay inside the seven-character SHORT_TEXT budget: the widest cases are "23h 59m" and
     * "59:59".
     */
    fun countdown(target: Instant, granularity: Granularity = Granularity.MINUTES): ComplicationText =
        TimeDifferenceComplicationText.Builder(granularity.style, CountDownTimeReference(target))
            .setMinimumTimeUnit(granularity.unit)
            .build()

    /**
     * How precisely a countdown is rendered.
     *
     * Each carries its own platform style and unit rather than having the caller map them, so the
     * two can never drift apart into a stopwatch that rounds to minutes.
     */
    enum class Granularity(val style: TimeDifferenceStyle, val unit: TimeUnit) {
        MINUTES(TimeDifferenceStyle.SHORT_DUAL_UNIT, TimeUnit.MINUTES),
        SECONDS(TimeDifferenceStyle.STOPWATCH, TimeUnit.SECONDS),
    }

    /**
     * Live elapsed time since [since].
     *
     * Defaults to seconds, unlike a countdown. The only thing that counts up here is the few minutes
     * after the Iqama, where the display is `Pray 3:12` — a span short enough that minutes alone
     * would sit on "3m" for a minute at a time and read as frozen.
     */
    fun countUp(since: Instant, granularity: Granularity = Granularity.SECONDS): ComplicationText =
        TimeDifferenceComplicationText.Builder(granularity.style, CountUpTimeReference(since))
            .setMinimumTimeUnit(granularity.unit)
            .build()

    /** Renders a phase's content as the complication's primary value. */
    fun forContent(
        content: PhaseContent,
        zone: ZoneId,
        granularity: Granularity = Granularity.MINUTES,
    ): ComplicationText = when (content) {
        is PhaseContent.AbsoluteTime -> clock(content.target, zone)
        is PhaseContent.CountDown -> countdown(content.target, granularity)
        is PhaseContent.CountUp -> countUp(content.since, granularity)
    }

    /**
     * The title beside the value — which is not always the prayer's name.
     *
     * The specified layouts are `Asr / 3:50`, `Iqama / 4:05` and `Pray 3:12`, so the title says what
     * the number *is* rather than repeating which prayer it belongs to. During the wait for an Adhan
     * that is the prayer; once it has been called the useful word is Iqama, and once the congregation
     * has begun it is Pray.
     *
     * Every value fits the seven-character SHORT_TEXT budget in both languages — `MAGHRIB` is
     * exactly 7 and so is `الإقامة`, which `ComplicationLabelsTest` pins.
     */
    fun titleFor(
        prayer: Prayer,
        content: PhaseContent,
        labels: ComplicationLabels = ComplicationLabels.Fallback,
    ): ComplicationText = plain(titleTextFor(prayer, content, labels))

    /**
     * The same choice as a plain string.
     *
     * Split out because [ComplicationText] is an Android type and cannot be constructed in a plain
     * unit test, while which word appears over the number is exactly the kind of thing that needs
     * asserting without a watch to look at.
     */
    fun titleTextFor(
        prayer: Prayer,
        content: PhaseContent,
        labels: ComplicationLabels = ComplicationLabels.Fallback,
    ): String = when {
        content is PhaseContent.CountUp -> labels.pray
        content.kind == TimeKind.IQAMA -> labels.iqama
        else -> labels.nameOf(prayer)
    }

    /**
     * Spells out what the terse text means, for screen readers.
     *
     * "ASR / 4:32" read literally conveys nothing; "Adhan at ASR" does.
     */
    fun describe(
        prayer: Prayer,
        content: PhaseContent,
        source: ScheduleSource,
        labels: ComplicationLabels = ComplicationLabels.Fallback,
    ): ComplicationText {
        // Derived from the same rule as the visible title, not a parallel one. These drifted:
        // the count-up after an Iqama showed "Pray" on screen and read out as "Iqama".
        val label = when {
            content is PhaseContent.CountUp -> labels.pray
            content.kind == TimeKind.IQAMA -> labels.iqama
            else -> labels.adhan
        }
        return plain(
            "$label · ${labels.nameOf(prayer)}${estimateSuffix(source, labels)}",
        )
    }

    fun plain(text: String): ComplicationText = PlainComplicationText.Builder(text).build()

    /**
     * Marks locally calculated times as estimates.
     *
     * Required by the project decision in RESEARCH.md §5: the offline fallback agrees with AlAdhan
     * to within three minutes, and a user is entitled to know which of the two they are reading. A
     * complication has no room for a badge, so it goes in the content description.
     */
    fun estimateSuffix(
        source: ScheduleSource,
        labels: ComplicationLabels = ComplicationLabels.Fallback,
    ): String = if (source.isEstimate) " (${labels.estimated})" else ""

    fun format(instant: Instant, zone: ZoneId): String = TIME_FORMAT.format(instant.atZone(zone))

    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm")
}
