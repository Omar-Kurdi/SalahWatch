package com.salahwatch.app.widget

import android.content.Context
import android.widget.RemoteViews
import java.time.Instant

/**
 * The schedule widget: today's timetable as a column, with the next Adhan or Iqama counting down.
 *
 * Resizable from about two cells square upwards. It does not clip as it shrinks — see [gridLayoutFor]
 * for what gives way and in what order. All six entries survive every size, because a timetable
 * missing its last prayer is worse than a small one.
 *
 * Kept under its original name so that widgets already placed on a home screen keep working: a
 * receiver's class name is the identity the launcher stores.
 */
public class PrayerWidgetProvider : BasePrayerWidgetProvider() {

    override val alarmRequestCode: Int = ALARM_REQUEST_CODE

    override fun buildViews(
        context: Context,
        content: WidgetContent,
        now: Instant,
        size: WidgetSize,
    ): RemoteViews = WidgetViews.build(context, content, now, gridLayoutFor(size))

    private companion object {
        const val ALARM_REQUEST_CODE = 0
    }
}
