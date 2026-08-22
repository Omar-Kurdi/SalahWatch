package com.salahwatch.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.salahwatch.app.MainActivity
import com.salahwatch.app.R
import com.salahwatch.app.widget.WidgetIds.ADHAN
import com.salahwatch.app.widget.WidgetIds.IQAMA
import com.salahwatch.app.widget.WidgetIds.NAME
import com.salahwatch.app.widget.WidgetIds.ROW
import com.salahwatch.core.calculation.TimeKind
import com.salahwatch.core.model.Prayer
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns a [WidgetContent] into RemoteViews.
 *
 * Nothing here decides anything — every question of *what* to show was already answered by
 * [widgetContentFor]. This file only knows how to say it.
 */
internal object WidgetViews {

    private const val TIME_PATTERN = "h:mm a"
    private const val DATE_PATTERN = "EEEE d MMMM"

    fun build(context: Context, content: WidgetContent, now: Instant): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_prayer)
        val locale = context.resources.configuration.locales[0]
        // Latin digits, for the same reason the app forces them — see Formatting.kt. It matters
        // more here: a widget's row heights are fixed by the layout, and Arabic-Indic digits in a
        // monospace column overflow it rather than merely looking large.
        val numeric = Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "latn").build()
        val time = DateTimeFormatter.ofPattern(TIME_PATTERN, numeric)
        val date = DateTimeFormatter.ofPattern(DATE_PATTERN, numeric)

        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))

        if (content.isEmpty) {
            return views.asEmptyState(context)
        }

        views.setViewVisibility(R.id.widget_empty, View.GONE)
        views.setTextViewText(R.id.widget_date, date.format(content.date))
        views.setTextViewText(R.id.widget_location, content.locationLabel.orEmpty())

        views.bindHeadline(context, content, now, time)
        views.bindRows(context, content, time)

        views.setViewVisibility(
            R.id.widget_notice,
            if (content.isEstimated) View.VISIBLE else View.GONE,
        )
        if (content.isEstimated) {
            views.setTextViewText(R.id.widget_notice, context.getString(R.string.label_estimated))
        }

        return views
    }

    /** No location yet. The timetable is hidden entirely rather than shown as a row of dashes. */
    private fun RemoteViews.asEmptyState(context: Context): RemoteViews = apply {
        setViewVisibility(R.id.widget_headline, View.GONE)
        setViewVisibility(R.id.widget_rows, View.GONE)
        setViewVisibility(R.id.widget_divider, View.GONE)
        setViewVisibility(R.id.widget_notice, View.GONE)
        setViewVisibility(R.id.widget_empty, View.VISIBLE)
        setTextViewText(R.id.widget_empty, context.getString(R.string.empty_location_title))
        setTextViewText(R.id.widget_date, "")
        setTextViewText(R.id.widget_location, "")
    }

    /**
     * The countdown.
     *
     * Exactly one of the two time views is visible at any moment, and which one is [WidgetContent]'s
     * decision. The `Chronometer` is handed a base in the `elapsedRealtime` timebase — not a wall
     * clock — which is what lets the system tick it while this process is not running at all.
     */
    private fun RemoteViews.bindHeadline(
        context: Context,
        content: WidgetContent,
        now: Instant,
        time: DateTimeFormatter,
    ) {
        val headline = content.headline
        if (headline == null) {
            setViewVisibility(R.id.widget_headline, View.GONE)
            return
        }

        setViewVisibility(R.id.widget_headline, View.VISIBLE)
        setTextViewText(
            R.id.widget_headline_label,
            context.getString(
                when (headline.kind) {
                    TimeKind.ADHAN -> R.string.label_adhan
                    TimeKind.IQAMA -> R.string.label_iqama
                },
            ),
        )
        setTextViewText(R.id.widget_headline_prayer, context.prayerName(headline.prayer))

        when (headline.style) {
            CountdownStyle.CLOCK -> {
                setViewVisibility(R.id.widget_countdown_ticker, View.GONE)
                setViewVisibility(R.id.widget_countdown_clock, View.VISIBLE)
                setTextViewText(
                    R.id.widget_countdown_clock,
                    time.format(headline.target.atZone(content.zone)),
                )
            }

            CountdownStyle.TICKING -> {
                setViewVisibility(R.id.widget_countdown_clock, View.GONE)
                setViewVisibility(R.id.widget_countdown_ticker, View.VISIBLE)
                val remaining = headline.target.toEpochMilli() - now.toEpochMilli()
                setChronometer(
                    R.id.widget_countdown_ticker,
                    SystemClock.elapsedRealtime() + remaining,
                    null,
                    true,
                )
                setChronometerCountDown(R.id.widget_countdown_ticker, true)
            }
        }
    }

    private fun RemoteViews.bindRows(
        context: Context,
        content: WidgetContent,
        time: DateTimeFormatter,
    ) {
        setViewVisibility(R.id.widget_rows, View.VISIBLE)
        setViewVisibility(R.id.widget_divider, View.VISIBLE)

        for (slot in ROW.indices) {
            val row = content.rows.getOrNull(slot)
            if (row == null) {
                setViewVisibility(ROW[slot], View.GONE)
                continue
            }

            setViewVisibility(ROW[slot], View.VISIBLE)
            setInt(
                ROW[slot],
                "setBackgroundResource",
                if (row.isNext) R.drawable.widget_row_highlight else 0,
            )

            val ink = context.getColor(
                when {
                    row.isNext -> R.color.widget_accent
                    row.hasPassed -> R.color.widget_ink_faint
                    else -> R.color.widget_ink
                },
            )

            setTextViewText(NAME[slot], context.prayerName(row.prayer))
            setTextColor(NAME[slot], ink)

            setTextViewText(ADHAN[slot], time.format(row.adhan.atZone(content.zone)))
            setTextColor(ADHAN[slot], ink)

            // Sunrise has no congregation, so the column is blank rather than filled with a dash
            // that would read as missing data — matching the in-app schedule.
            setTextViewText(
                IQAMA[slot],
                row.iqama?.let { time.format(it.atZone(content.zone)) } ?: "",
            )
            setTextColor(
                IQAMA[slot],
                context.getColor(
                    if (row.hasPassed && !row.isNext) R.color.widget_ink_faint else R.color.widget_ink_muted,
                ),
            )
        }
    }

    private fun Context.prayerName(prayer: Prayer): String = getString(
        when (prayer) {
            Prayer.FAJR -> R.string.prayer_fajr
            Prayer.SUNRISE -> R.string.prayer_sunrise
            Prayer.DHUHR -> R.string.prayer_dhuhr
            Prayer.ASR -> R.string.prayer_asr
            Prayer.MAGHRIB -> R.string.prayer_maghrib
            Prayer.ISHA -> R.string.prayer_isha
        },
    )

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/**
 * The six fixed row slots.
 *
 * RemoteViews cannot add children at runtime, so the layout declares one slot per line of the
 * timetable and the binder fills as many as the day has. Six is not arbitrary: it is the five
 * prayers plus sunrise, and it is asserted against [Prayer] in the widget's tests.
 */
internal object WidgetIds {
    val ROW = intArrayOf(
        R.id.row_0, R.id.row_1, R.id.row_2, R.id.row_3, R.id.row_4, R.id.row_5,
    )
    val NAME = intArrayOf(
        R.id.row_0_name, R.id.row_1_name, R.id.row_2_name,
        R.id.row_3_name, R.id.row_4_name, R.id.row_5_name,
    )
    val ADHAN = intArrayOf(
        R.id.row_0_adhan, R.id.row_1_adhan, R.id.row_2_adhan,
        R.id.row_3_adhan, R.id.row_4_adhan, R.id.row_5_adhan,
    )
    val IQAMA = intArrayOf(
        R.id.row_0_iqama, R.id.row_1_iqama, R.id.row_2_iqama,
        R.id.row_3_iqama, R.id.row_4_iqama, R.id.row_5_iqama,
    )
}
