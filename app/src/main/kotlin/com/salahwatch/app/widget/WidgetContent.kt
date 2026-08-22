package com.salahwatch.app.widget

import com.salahwatch.core.calculation.ComplicationMode
import com.salahwatch.core.calculation.PhaseContent
import com.salahwatch.core.calculation.PhaseTimelineBuilder
import com.salahwatch.core.calculation.PrayerCalendar
import com.salahwatch.core.calculation.PrayerOccurrence
import com.salahwatch.core.calculation.SecondsWindow
import com.salahwatch.core.calculation.TimeKind
import com.salahwatch.core.model.Prayer
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything the home screen widget draws, with no Android type anywhere in sight.
 *
 * Deliberately a pure function of a calendar and an instant. A widget is one of the hardest things
 * in an Android app to observe — no debugger, no recomposition, and a render that happens in the
 * launcher's process — so the logic that decides *what* it says is kept where it can be tested
 * directly, and [WidgetViews] is left with nothing to do but set text on views.
 */
internal data class WidgetContent(
    val date: LocalDate,
    val zone: ZoneId,
    /** The full day, sunrise included, in time order. */
    val rows: List<WidgetRow>,
    /** The countdown at the top, or null when the day is over or nothing is cached. */
    val headline: WidgetHeadline?,
    val locationLabel: String?,
    val isEstimated: Boolean,
    /**
     * When this rendering stops being true.
     *
     * The widget arms one alarm for this instant and does nothing at all until then. It is the
     * earlier of two moments: the end of the current phase — an Adhan arriving, an Iqama passing —
     * and the point where a countdown crosses into its seconds window and the display has to hand
     * over to a ticking `Chronometer`. Null when there is nothing further to wait for.
     */
    val validUntil: Instant?,
) {
    /** True when there is nothing to show and the widget must invite the user to set a location. */
    val isEmpty: Boolean get() = rows.isEmpty()
}

/** One line of the timetable. */
internal data class WidgetRow(
    val prayer: Prayer,
    val adhan: Instant,
    /** Null for Sunrise, which has no congregation. */
    val iqama: Instant?,
    val isNext: Boolean,
    val hasPassed: Boolean,
    /** How long until this prayer, or null once it has passed. See [RowCountdown]. */
    val countdown: RowCountdown?,
)

/**
 * The countdown shown against one prayer.
 *
 * [isLive] is the whole point of this type. Only the prayer currently in play gets a ticking,
 * to-the-second countdown; every other prayer shows a still figure that says how long, in units
 * coarse enough to stay true between redraws. Two prayers are never close enough at once for a
 * second one to need seconds, so five ticking columns would be five times the visual noise for no
 * information — and a still figure costs nothing to keep honest, because its precision is chosen to
 * match how often it is redrawn.
 */
internal data class RowCountdown(
    val target: Instant,
    val kind: TimeKind,
    /** True for the prayer in play: rendered as a self-ticking `Chronometer`, seconds and all. */
    val isLive: Boolean,
)

/**
 * The prayer being counted to, and how precisely.
 *
 * [kind] is what makes the widget follow the same Adhan-then-Iqama cycle as complication (e): once
 * an Adhan has been called the countdown does not jump to the next prayer, it turns to that
 * prayer's own Iqama, and only once *that* has passed does the next prayer take over.
 */
internal data class WidgetHeadline(
    val prayer: Prayer,
    val kind: TimeKind,
    val target: Instant,
    val style: CountdownStyle,
)

/**
 * How the remaining time is drawn — and the reason there are exactly two.
 *
 * A home screen widget cannot redraw itself once a minute; the platform floor for periodic updates
 * is half an hour, and anything finer means waking this process up on a timer, which is the one
 * thing this app refuses to do. So a live "1h 04m" that stays honest is not available.
 *
 * What *is* available is a `Chronometer`, which the system ticks with no involvement from this
 * process at all — but it always shows seconds. Hence the split, which lands on exactly the
 * threshold the watch complications already use: outside [SecondsWindow] the Adhan's clock time,
 * which is never stale because it never changes; inside it, a Chronometer counting down in `mm:ss`.
 */
internal enum class CountdownStyle {
    /** The target's wall-clock time, e.g. "3:50 PM". Static, and correct for as long as it shows. */
    CLOCK,

    /** A self-ticking countdown, showing seconds. */
    TICKING,
}

/**
 * Works out what the widget should say at [now].
 *
 * The Adhan/Iqama cycle is not re-derived here: it is taken from [PhaseTimelineBuilder] in
 * [ComplicationMode.ADHAN_THEN_IQAMA_COUNTDOWN], the same code that drives the watch. That is
 * deliberate. "When a prayer or its Iqama ends, move to the next" is the rule this project has had
 * to fix more than once, and a second implementation of it on the phone would be a second thing to
 * get wrong.
 */
internal fun widgetContentFor(
    calendar: PrayerCalendar,
    now: Instant,
    date: LocalDate,
    locationLabel: String?,
    builder: PhaseTimelineBuilder = PhaseTimelineBuilder(),
): WidgetContent {
    val today = calendar.occurrences.filter { it.date == date }
    val zone = today.firstOrNull()?.zone ?: ZoneId.systemDefault()

    val headline = headlineAt(calendar, now, builder)
    val next = headline?.let { head -> today.markedFor(head) }

    val rows = today.map { occurrence ->
        val isNext = occurrence === next
        WidgetRow(
            prayer = occurrence.prayer,
            adhan = occurrence.adhan,
            iqama = occurrence.iqama,
            isNext = isNext,
            hasPassed = occurrence.adhan < now,
            countdown = countdownFor(occurrence, isNext, headline, now),
        )
    }

    return WidgetContent(
        date = date,
        zone = zone,
        rows = rows,
        headline = headline,
        locationLabel = locationLabel,
        isEstimated = today.any { it.source.isEstimate },
        validUntil = validUntil(calendar, now, builder, rows),
    )
}

/**
 * A still countdown: a number, its unit, and the instant the number changes.
 *
 * The unit is deliberately coarse, and [changesAt] is what makes that honest rather than lazy. The
 * displayed precision and the redraw are derived from the same figure, so the widget is never
 * showing a value that has quietly gone stale — it says "5h" for exactly as long as it is five
 * hours, and is redrawn the moment it becomes four.
 *
 * The alternative, a live figure accurate to the minute, would mean redrawing every sixty seconds
 * for every prayer on screen. That is roughly fourteen hundred wakeups a day against this app's
 * central claim that it does not wake up to tick a clock.
 */
internal data class CoarseCountdown(
    val amount: Long,
    val unit: CoarseUnit,
    val changesAt: Instant,
)

internal enum class CoarseUnit {
    HOURS,
    MINUTES,

    /** Less than one step away. Shown as "under 5m" rather than counting down to nothing. */
    IMMINENT,
}

/**
 * Rounds the wait down to something that stays true between redraws.
 *
 * Whole hours at range, five-minute steps inside the last hour. Rounding *down* is what makes it
 * readable as a floor — "3h" means at least three hours — rather than as an approximation that
 * could be over or under.
 */
internal fun coarseCountdown(from: Instant, to: Instant): CoarseCountdown? {
    val remaining = Duration.between(from, to)
    val hours = remaining.toHours()
    val step = remaining.toMinutes() / COARSE_STEP_MINUTES * COARSE_STEP_MINUTES

    return when {
        remaining.isNegative || remaining.isZero -> null

        remaining >= Duration.ofHours(1) ->
            CoarseCountdown(hours, CoarseUnit.HOURS, to.minus(Duration.ofHours(hours)))

        step <= 0 -> CoarseCountdown(COARSE_STEP_MINUTES, CoarseUnit.IMMINENT, to)

        else -> CoarseCountdown(step, CoarseUnit.MINUTES, to.minus(Duration.ofMinutes(step)))
    }
}

/** How finely a still countdown steps inside the last hour. */
private const val COARSE_STEP_MINUTES = 5L

/**
 * The row the headline is counting towards.
 *
 * Matching on the exact instant is tried first, because within a day it is unambiguous. When that
 * fails the match falls back to the prayer itself, and that fallback is not a leniency — it is the
 * only thing that makes the widget useful after the last prayer of the day. From Isha until
 * midnight the headline counts to *tomorrow's* Fajr, which is not in today's list at any instant;
 * without the fallback nothing at all is marked and the whole widget greys out, showing a day that
 * is over and nothing coming.
 */
private fun List<PrayerOccurrence>.markedFor(headline: WidgetHeadline): PrayerOccurrence? {
    val exact = firstOrNull {
        when (headline.kind) {
            TimeKind.ADHAN -> it.adhan == headline.target
            TimeKind.IQAMA -> it.iqama == headline.target
        }
    }
    return exact ?: firstOrNull { it.prayer == headline.prayer }
}

/**
 * How long until [occurrence], or null once it is behind us.
 *
 * The marked prayer takes its target from the headline, so it follows the Adhan → Iqama cycle the
 * phase timeline defines rather than a second reading of it. Every other prayer still ahead counts
 * to its own Adhan, and does so as a still figure.
 */
private fun countdownFor(
    occurrence: PrayerOccurrence,
    isNext: Boolean,
    headline: WidgetHeadline?,
    now: Instant,
): RowCountdown? = when {
    isNext && headline != null ->
        RowCountdown(target = headline.target, kind = headline.kind, isLive = true)

    // Sunrise is shown but never counted down to — it is not a prayer.
    occurrence.prayer.isPrayer && occurrence.adhan > now ->
        RowCountdown(target = occurrence.adhan, kind = TimeKind.ADHAN, isLive = false)

    else -> null
}

private fun headlineAt(
    calendar: PrayerCalendar,
    now: Instant,
    builder: PhaseTimelineBuilder,
): WidgetHeadline? {
    val phase = currentPhase(calendar, now, builder) ?: return null
    val content = phase.content as? PhaseContent.CountDown ?: return null
    val window = SecondsWindow.forKind(content.kind)

    return WidgetHeadline(
        prayer = phase.prayer,
        kind = content.kind,
        target = content.target,
        style = if (Duration.between(now, content.target) <= window) {
            CountdownStyle.TICKING
        } else {
            CountdownStyle.CLOCK
        },
    )
}

/**
 * The next moment the widget's rendering would become wrong.
 *
 * Two candidates, and the earlier wins. Missing either one is a visibly stale widget: without the
 * phase end it keeps counting to an Adhan that has already been called, and without the window
 * crossing the countdown never starts ticking.
 */
private fun validUntil(
    calendar: PrayerCalendar,
    now: Instant,
    builder: PhaseTimelineBuilder,
    rows: List<WidgetRow>,
): Instant? {
    val phase = currentPhase(calendar, now, builder)
    val content = phase?.content

    val windowOpens = (content as? PhaseContent.CountDown)
        ?.let { it.target.minus(SecondsWindow.forKind(it.kind)) }
        ?.takeIf { it > now }

    // Every still countdown on screen is also a deadline: the instant its number changes is an
    // instant this rendering stops being true. Missing these is what would leave "3h" on screen
    // for the following hour.
    val stillChanges = rows
        .mapNotNull { it.countdown }
        .filterNot { it.isLive }
        .mapNotNull { coarseCountdown(now, it.target)?.changesAt }
        .filter { it > now }

    return (listOfNotNull(phase?.end, windowOpens) + stillChanges).minOrNull()
}

private fun currentPhase(
    calendar: PrayerCalendar,
    now: Instant,
    builder: PhaseTimelineBuilder,
) = builder
    .build(
        calendar = calendar,
        mode = ComplicationMode.ADHAN_THEN_IQAMA_COUNTDOWN,
        from = now,
        until = now.plus(LOOKAHEAD),
    )
    .firstOrNull { now in it }

/** Far enough to always contain the current phase, short enough to stay cheap to build. */
private val LOOKAHEAD: Duration = Duration.ofDays(2)
