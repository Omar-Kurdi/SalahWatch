package com.salahwatch.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.salahwatch.app.MainActivity
import com.salahwatch.app.R
import com.salahwatch.core.calculation.TimeKind
import com.salahwatch.core.model.Prayer
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns a [WidgetContent] into the one-row strip.
 *
 * Like [WidgetViews], this decides nothing: which prayer is next, what is being counted to and
 * whether that countdown should tick were all settled by [widgetContentFor]. The strip differs from
 * the schedule widget only in how it says the same thing.
 */
internal object StripWidgetViews {

    private const val TIME_PATTERN = "h:mm a"
    private const val TIME_PATTERN_BARE = "h:mm"

    /**
     * The five prayers, in order.
     *
     * Sunrise is deliberately absent — see [PrayerStripWidgetProvider]. Adding it back is a matter
     * of putting it in this list and adding a sixth column to the layout.
     */
    private val COLUMNS = listOf(
        Prayer.FAJR,
        Prayer.DHUHR,
        Prayer.ASR,
        Prayer.MAGHRIB,
        Prayer.ISHA,
    )

    fun build(
        context: Context,
        content: WidgetContent,
        now: Instant,
        layout: StripLayout,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_prayer_strip)
        val locale = context.resources.configuration.locales[0]
        val numeric = Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "latn").build()
        val pattern = if (layout.showMeridiem) TIME_PATTERN else TIME_PATTERN_BARE
        val time = DateTimeFormatter.ofPattern(pattern, numeric)

        views.setOnClickPendingIntent(R.id.strip_root, openApp(context))

        if (content.isEmpty) {
            views.setViewVisibility(R.id.strip_columns, View.GONE)
            views.setViewVisibility(R.id.strip_empty, View.VISIBLE)
            views.setTextViewText(R.id.strip_empty, context.getString(R.string.empty_location_title))
            return views
        }

        views.setViewVisibility(R.id.strip_empty, View.GONE)
        views.setViewVisibility(R.id.strip_columns, View.VISIBLE)

        val byPrayer = content.rows.associateBy { it.prayer }
        for ((slot, prayer) in COLUMNS.withIndex()) {
            views.bindColumn(context, slot, byPrayer[prayer], prayer, content, now, time, layout)
        }

        return views
    }

    /** "3h" / "45m" / "under 5m", in the app's language and always with European digits. */
    private fun Context.coarseText(countdown: CoarseCountdown): String {
        val numeric = Locale.Builder()
            .setLocale(resources.configuration.locales[0])
            .setUnicodeLocaleKeyword("nu", "latn")
            .build()

        val template = getString(
            when (countdown.unit) {
                CoarseUnit.HOURS -> R.string.countdown_hours_short
                CoarseUnit.MINUTES -> R.string.countdown_minutes_short
                CoarseUnit.IMMINENT -> R.string.countdown_under_minutes_short
            },
        )
        return String.format(numeric, template, countdown.amount)
    }

    @Suppress("LongParameterList")
    private fun RemoteViews.bindColumn(
        context: Context,
        slot: Int,
        row: WidgetRow?,
        prayer: Prayer,
        content: WidgetContent,
        now: Instant,
        time: DateTimeFormatter,
        layout: StripLayout,
    ) {
        val ids = StripIds
        if (row == null) {
            setViewVisibility(ids.COLUMN[slot], View.INVISIBLE)
            return
        }

        setViewVisibility(ids.COLUMN[slot], View.VISIBLE)

        // The box that answers "which one is coming up" without the user reading a single time.
        setInt(
            ids.COLUMN[slot],
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

        // The icon is the primary signal that a countdown has moved from the Adhan to the Iqama:
        // the prayer's own glyph becomes a mosque. Colour carries the same information a second
        // time, so neither has to be noticed on its own.
        val countingToIqama = row.countdown?.kind == TimeKind.IQAMA
        setImageViewResource(
            ids.ICON[slot],
            if (countingToIqama) R.drawable.ic_prayer_iqama else prayer.icon(),
        )
        // Tinting from code rather than by `android:tint` in the vector: that attribute is not
        // reliably honoured for RemoteViews on API 29–30, so the icon would ignore the theme.
        setInt(
            ids.ICON[slot],
            "setColorFilter",
            if (countingToIqama) context.getColor(R.color.widget_iqama) else ink,
        )

        setTextViewText(ids.NAME[slot], context.prayerName(prayer))
        setTextColor(
            ids.NAME[slot],
            if (row.isNext) ink else context.getColor(R.color.widget_ink_muted),
        )
        setTextViewTextSize(ids.NAME[slot], TypedValue.COMPLEX_UNIT_SP, layout.nameTextSp)

        // The marked prayer's time is the one its countdown refers to, which after the last prayer
        // of the day is tomorrow's — see WidgetContent.markedFor.
        val shown = if (row.isNext) row.countdown?.target ?: row.adhan else row.adhan
        val timeText = if (row.isNext && row.countdown?.kind == TimeKind.IQAMA) {
            time.format(row.adhan.atZone(content.zone))
        } else {
            time.format(shown.atZone(content.zone))
        }
        setTextViewText(ids.TIME[slot], timeText)
        setTextColor(ids.TIME[slot], ink)
        setTextViewTextSize(ids.TIME[slot], TypedValue.COMPLEX_UNIT_SP, layout.timeTextSp)

        bindCountdown(context, slot, row, now, layout)
    }

    /*
     * The countdown line: ticking for the prayer in play, still for the rest, absent once passed.
     *
     * Only one prayer is ever close enough for seconds to mean anything, so only that one gets a
     * `Chronometer`. The others would otherwise tick five columns of seconds in unison — motion
     * carrying no information, for a prayer hours away.
     */

    private fun RemoteViews.bindCountdown(
        context: Context,
        slot: Int,
        row: WidgetRow,
        now: Instant,
        layout: StripLayout,
    ) {
        val countdown = row.countdown
        val coarse = countdown
            ?.takeUnless { it.isLive }
            ?.let { coarseCountdown(now, it.target) }

        when {
            countdown == null || (!countdown.isLive && coarse == null) -> hideCountdown(slot)
            countdown.isLive -> bindTicking(context, slot, countdown, now, layout)
            else -> bindStill(context, slot, requireNotNull(coarse), layout)
        }
    }

    private fun RemoteViews.hideCountdown(slot: Int) {
        setViewVisibility(StripIds.LIVE[slot], View.GONE)
        setViewVisibility(StripIds.STILL[slot], View.GONE)
    }

    /** The prayer in play: a `Chronometer` the system ticks, seconds and all. */
    private fun RemoteViews.bindTicking(
        context: Context,
        slot: Int,
        countdown: RowCountdown,
        now: Instant,
        layout: StripLayout,
    ) {
        val live = StripIds.LIVE[slot]
        setViewVisibility(StripIds.STILL[slot], View.GONE)
        setViewVisibility(live, View.VISIBLE)
        setTextViewTextSize(live, TypedValue.COMPLEX_UNIT_SP, layout.countdownTextSp)
        setTextColor(
            live,
            context.getColor(
                if (countdown.kind == TimeKind.IQAMA) R.color.widget_iqama else R.color.widget_accent,
            ),
        )
        setChronometer(
            live,
            SystemClock.elapsedRealtime() + (countdown.target.toEpochMilli() - now.toEpochMilli()),
            null,
            true,
        )
        setChronometerCountDown(live, true)
    }

    /** Everything further out: a still figure, redrawn only when the figure itself changes. */
    private fun RemoteViews.bindStill(
        context: Context,
        slot: Int,
        coarse: CoarseCountdown,
        layout: StripLayout,
    ) {
        val still = StripIds.STILL[slot]
        setViewVisibility(StripIds.LIVE[slot], View.GONE)
        setViewVisibility(still, View.VISIBLE)
        setTextViewText(still, context.coarseText(coarse))
        setTextColor(still, context.getColor(R.color.widget_ink_muted))
        setTextViewTextSize(still, TypedValue.COMPLEX_UNIT_SP, layout.countdownTextSp)
    }

    /**
     * One glyph per prayer.
     *
     * Deliberately keyed to the sun's height rather than to its detail: a full sun and a slightly
     * lower sun are indistinguishable at fourteen density-independent pixels, so Dhuhr and Asr are
     * separated by *where* the sun sits in the frame, which does read at that size.
     */
    private fun Prayer.icon(): Int = when (this) {
        Prayer.FAJR -> R.drawable.ic_prayer_fajr
        Prayer.SUNRISE -> R.drawable.ic_prayer_sunrise
        Prayer.DHUHR -> R.drawable.ic_prayer_dhuhr
        Prayer.ASR -> R.drawable.ic_prayer_asr
        Prayer.MAGHRIB -> R.drawable.ic_prayer_maghrib
        Prayer.ISHA -> R.drawable.ic_prayer_isha
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
        STRIP_REQUEST_CODE,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private const val STRIP_REQUEST_CODE = 1
}

/** The strip's five fixed columns. Same reasoning as [WidgetIds]. */
internal object StripIds {
    val COLUMN = intArrayOf(
        R.id.strip_0, R.id.strip_1, R.id.strip_2, R.id.strip_3, R.id.strip_4,
    )
    val ICON = intArrayOf(
        R.id.strip_0_icon, R.id.strip_1_icon, R.id.strip_2_icon,
        R.id.strip_3_icon, R.id.strip_4_icon,
    )
    val NAME = intArrayOf(
        R.id.strip_0_name, R.id.strip_1_name, R.id.strip_2_name,
        R.id.strip_3_name, R.id.strip_4_name,
    )
    val TIME = intArrayOf(
        R.id.strip_0_time, R.id.strip_1_time, R.id.strip_2_time,
        R.id.strip_3_time, R.id.strip_4_time,
    )
    /** The still countdown for prayers further out. */
    val STILL = intArrayOf(
        R.id.strip_0_still, R.id.strip_1_still, R.id.strip_2_still,
        R.id.strip_3_still, R.id.strip_4_still,
    )

    /** The ticking countdown, used by the prayer in play and no other. */
    val LIVE = intArrayOf(
        R.id.strip_0_live, R.id.strip_1_live, R.id.strip_2_live,
        R.id.strip_3_live, R.id.strip_4_live,
    )
}
