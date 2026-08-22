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
    val next = headline?.let { head ->
        today.firstOrNull { it.prayer == head.prayer && it.isThePrayerBehind(head) }
    }

    return WidgetContent(
        date = date,
        zone = zone,
        rows = today.map { occurrence ->
            WidgetRow(
                prayer = occurrence.prayer,
                adhan = occurrence.adhan,
                iqama = occurrence.iqama,
                isNext = occurrence === next,
                hasPassed = occurrence.adhan < now,
            )
        },
        headline = headline,
        locationLabel = locationLabel,
        isEstimated = today.any { it.source.isEstimate },
        validUntil = validUntil(calendar, now, builder),
    )
}

/**
 * Whether this occurrence is the one the headline is counting towards.
 *
 * Matching on the prayer alone is not enough across midnight: at 00:30 the headline may be counting
 * to *tomorrow's* Fajr while today's Fajr sits in the list, and highlighting the wrong one would
 * mark a prayer eighteen hours past as the one coming up.
 */
private fun PrayerOccurrence.isThePrayerBehind(headline: WidgetHeadline): Boolean = when (headline.kind) {
    TimeKind.ADHAN -> adhan == headline.target
    TimeKind.IQAMA -> iqama == headline.target
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
): Instant? {
    val phase = currentPhase(calendar, now, builder) ?: return null
    val content = phase.content

    val windowOpens = (content as? PhaseContent.CountDown)
        ?.let { it.target.minus(SecondsWindow.forKind(it.kind)) }
        ?.takeIf { it > now }

    return listOfNotNull(phase.end, windowOpens).min()
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
