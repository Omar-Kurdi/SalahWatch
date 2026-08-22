package com.salahwatch.app.widget

import android.content.Context
import android.widget.RemoteViews
import java.time.Instant

/**
 * The strip widget: one row, five prayers across, an icon and a time under each.
 *
 * A different question from the schedule widget rather than a smaller version of it. The schedule
 * answers "what does today look like"; the strip answers "where am I in the day", at a glance, from
 * a single cell of home screen. The prayer being counted to is boxed, and its countdown appears
 * beneath it once it is close enough to be worth showing to the second.
 *
 * Sunrise is not among the five. It is in the app and in the schedule widget, where there is room
 * for it — here five columns leave each one wide enough for a time *and* a countdown, and six do
 * not.
 */
public class PrayerStripWidgetProvider : BasePrayerWidgetProvider() {

    override val alarmRequestCode: Int = ALARM_REQUEST_CODE

    override fun buildViews(
        context: Context,
        content: WidgetContent,
        now: Instant,
        size: WidgetSize,
    ): RemoteViews = StripWidgetViews.build(context, content, now, stripLayoutFor(size))

    private companion object {
        /** Distinct from the schedule widget's, so the two alarms cannot replace each other. */
        const val ALARM_REQUEST_CODE = 1
    }
}
