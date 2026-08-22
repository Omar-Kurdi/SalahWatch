package com.salahwatch.core.calculation

import java.time.Duration

/**
 * When a countdown stops being minutes and starts being seconds.
 *
 * This lives in the shared module because it is a *product* rule, not a rendering detail, and three
 * surfaces have to agree on it: the watch complications, the phone widget, and the phone app. Each
 * of them expresses it with a different mechanism — a split timeline entry on the watch, a
 * `Chronometer` in the widget's RemoteViews — so if the numbers were defined next to any one of
 * those mechanisms the other two would drift from it silently.
 *
 * The two windows differ because the two waits differ. A wait for an Adhan is long, and only its
 * last quarter of an hour is worth counting precisely. A wait for an Iqama is short to begin with,
 * so its window is shorter — and an Iqama offset at or below [IQAMA] never reaches a minutes phase
 * at all: it counts in seconds from the moment the Adhan is called, which is the intent rather than
 * an edge case.
 */
public object SecondsWindow {
    /** How long before an Adhan the countdown starts showing seconds. */
    public val ADHAN: Duration = Duration.ofMinutes(ADHAN_MINUTES)

    /** How long before an Iqama the countdown starts showing seconds. */
    public val IQAMA: Duration = Duration.ofMinutes(IQAMA_MINUTES)

    /** The window that applies to [kind]. */
    public fun forKind(kind: TimeKind): Duration = when (kind) {
        TimeKind.ADHAN -> ADHAN
        TimeKind.IQAMA -> IQAMA
    }

    private const val ADHAN_MINUTES = 15L
    private const val IQAMA_MINUTES = 10L
}
