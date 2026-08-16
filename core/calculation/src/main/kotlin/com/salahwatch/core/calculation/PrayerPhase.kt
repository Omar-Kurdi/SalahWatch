package com.salahwatch.core.calculation

import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.ScheduleSource
import java.time.Instant

/** Whether a phase is talking about the call to prayer or the start of the congregation. */
public enum class TimeKind { ADHAN, IQAMA }

/**
 * What a complication should show during a phase.
 *
 * Each variant maps onto exactly one Wear OS text primitive, chosen so that the *platform* does the
 * per-second re-rendering and this process never wakes up to tick a clock:
 *
 * | Variant          | Renders as                                              |
 * |------------------|---------------------------------------------------------|
 * | [AbsoluteTime]   | plain text, e.g. `3:50`                                  |
 * | [CountDown]      | `TimeDifferenceComplicationText` (countDownTimeReference)|
 * | [CountUp]        | `TimeDifferenceComplicationText` (countUpTimeReference)  |
 *
 * This is why the app can satisfy SPEC.md's "DO NOT implement a service that wakes up every second"
 * without compromise, and why the 300-second `UPDATE_PERIOD_SECONDS` floor stops mattering.
 */
public sealed interface PhaseContent {
    /** The kind of moment being described, for labelling. */
    public val kind: TimeKind

    /** Show a wall-clock time, e.g. "3:50". */
    public data class AbsoluteTime(val target: Instant, override val kind: TimeKind) : PhaseContent

    /** Count down to [target], e.g. "58m", or "4:59" in the final minutes. */
    public data class CountDown(val target: Instant, override val kind: TimeKind) : PhaseContent

    /** Count up from [since], e.g. "3:12" since the congregation began. */
    public data class CountUp(val since: Instant, override val kind: TimeKind) : PhaseContent
}

/**
 * A time-bounded segment of complication content — the direct precursor to a `TimelineEntry`.
 *
 * [start] is inclusive and [end] exclusive. A list of these covering the next couple of days is all
 * the watch needs to render every prayer transition with no further work from this app.
 *
 * [arcFrom] and [arcTo] describe something different from [start] and [end], and the distinction
 * matters. They are the span a **gauge** measures, which is the whole logical segment — the entire
 * wait for the Adhan, say — while [start] and [end] are only the slice of it this particular entry
 * is on screen for. The two diverge whenever an entry is subdivided: a countdown is split so that it
 * can switch to seconds in its final minutes, and if the arc were measured from the entry it would
 * empty itself and start again at that switch. Measured from the segment, it sweeps once, smoothly.
 */
public data class PrayerPhase(
    val start: Instant,
    val end: Instant,
    val prayer: Prayer,
    val content: PhaseContent,
    val source: ScheduleSource,
    val arcFrom: Instant = start,
    val arcTo: Instant = end,
) {
    init {
        require(end > start) { "Phase for ${prayer.shortName} must have positive duration" }
        require(arcTo > arcFrom) { "Arc for ${prayer.shortName} must have positive duration" }
    }

    public operator fun contains(instant: Instant): Boolean = instant >= start && instant < end
}

/**
 * The five complications the app offers, as the project owner specified them.
 *
 * These are modes of one timeline builder rather than five unrelated implementations, because they
 * differ only in which boundaries exist inside a prayer's span. Every one of them is a *timeline*:
 * the provider hands the platform every transition for the next two days and is not consulted again.
 * That is not merely an efficiency — a complication that answers once and is never asked again keeps
 * showing the prayer that was next when it was added, which is exactly how the previous
 * "Current Prayer" provider came to be broken.
 */
public enum class ComplicationMode {
    /** **(a) Pray Time** — the next prayer and its Adhan time, e.g. `Asr / 3:50`. */
    PRAY_TIME,

    /**
     * **(b) Adhan Time/Countdown** — the Adhan's clock time, becoming a countdown
     * [PhaseTuning.countdownLeadMinutes] before it, then the next prayer's time.
     */
    ADHAN_TIME_THEN_COUNTDOWN,

    /**
     * **(c) Prayer Progress** — the full cycle.
     *
     * The Adhan's clock time, then a countdown from [PhaseTuning.progressLeadMinutes] out, then a
     * countdown to the Iqama, then time elapsed since it for
     * [PhaseTuning.postIqamaMinutes] — and then the next prayer, repeating.
     */
    PRAYER_PROGRESS,

    /** **(d) Adhan Countdown** — always a countdown to the next Adhan. */
    ADHAN_COUNTDOWN,

    /** **(e) Adhan / Iqama Countdown** — a countdown to the Adhan, then one to its Iqama. */
    ADHAN_THEN_IQAMA_COUNTDOWN,
}

/**
 * Tunables for [PhaseTimelineBuilder]. Defaults are the values the five complications were
 * specified with.
 *
 * There are two separate leads rather than one because (b) and (c) genuinely differ: (b) shows a
 * clock time nearly all day and turns into a countdown only near the Adhan, whereas (c) is a
 * progress display and spends most of the hour before each prayer counting down.
 */
public data class PhaseTuning(
    /**
      * How long before the Adhan a countdown replaces the clock time in mode (b).
      *
      * Thirty minutes, not the ten originally specified. Ten was short enough that the countdown
      * was easy to never catch — the complication read as a static clock time and was
      * indistinguishable from Prayer Time. Ten is also shorter than the fifteen-minute window in
      * which countdowns switch to `mm:ss`, so the minutes phase would have had no room to exist.
      */
    val countdownLeadMinutes: Long = DEFAULT_COUNTDOWN_LEAD_MINUTES,

    /** The same, for mode (c), which starts counting down an hour out. */
    val progressLeadMinutes: Long = DEFAULT_PROGRESS_LEAD_MINUTES,

    /** How long after the Iqama the elapsed-time display persists in mode (c). */
    val postIqamaMinutes: Long = DEFAULT_POST_IQAMA_MINUTES,
) {
    init {
        require(countdownLeadMinutes > 0) { "countdownLeadMinutes must be positive" }
        require(progressLeadMinutes > 0) { "progressLeadMinutes must be positive" }
        require(postIqamaMinutes >= 0) { "postIqamaMinutes cannot be negative" }
    }

    /** The lead that applies to [mode], or null where the mode has no clock-time phase to replace. */
    public fun leadFor(mode: ComplicationMode): Long? = when (mode) {
        ComplicationMode.ADHAN_TIME_THEN_COUNTDOWN -> countdownLeadMinutes
        ComplicationMode.PRAYER_PROGRESS -> progressLeadMinutes
        ComplicationMode.PRAY_TIME,
        ComplicationMode.ADHAN_COUNTDOWN,
        ComplicationMode.ADHAN_THEN_IQAMA_COUNTDOWN,
        -> null
    }

    public companion object {
        public const val DEFAULT_COUNTDOWN_LEAD_MINUTES: Long = 30
        public const val DEFAULT_PROGRESS_LEAD_MINUTES: Long = 59
        public const val DEFAULT_POST_IQAMA_MINUTES: Long = 5
    }
}
