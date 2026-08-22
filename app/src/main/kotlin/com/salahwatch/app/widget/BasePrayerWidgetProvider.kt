package com.salahwatch.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.salahwatch.core.database.ScheduleStore
import com.salahwatch.core.model.effectiveLocation
import com.salahwatch.core.preferences.LanguageOverride
import com.salahwatch.core.preferences.PreferencesRepository
import com.salahwatch.data.prayersync.PrayerSyncWorker
import com.salahwatch.data.prayertimes.PrayerScheduleRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Everything both home screen widgets do, which is everything except drawing.
 *
 * **How they stay current without a timer.** Two mechanisms, and between them this process sleeps.
 *
 * The seconds are the system's job. A `Chronometer` in the RemoteViews ticks in the launcher's
 * process against `elapsedRealtime`, so a countdown shows seconds while this app is not running at
 * all. That is the same bargain the watch complications strike with their timeline entries, and it
 * is why `updatePeriodMillis` is zero rather than the thirty-minute platform floor.
 *
 * The transitions are an alarm. [WidgetContent.validUntil] names the one instant at which the
 * current rendering stops being true — an Adhan arriving, an Iqama passing, or a countdown reaching
 * the point where it should start ticking — and exactly one alarm is armed for it. Roughly a dozen
 * a day, rather than one a minute.
 *
 * The alarm is **inexact**, deliberately. `SCHEDULE_EXACT_ALARM` is not granted by default from API
 * 33, and `USE_EXACT_ALARM` is restricted by Play policy to alarm and calendar apps; claiming it for
 * a widget would be a review risk taken for no visible gain. A late alarm is bounded rather than
 * corrupting: a `Chronometer` past its base counts back up from zero, so the countdown is seen to
 * bounce off zero and is corrected on the next redraw.
 *
 * Subclasses supply only [buildViews] and an [alarmRequestCode]. Everything keyed to a provider —
 * the widget ids it owns, the alarm it arms — is resolved from `javaClass` rather than a constant,
 * so the two providers cannot end up sharing one alarm that redraws only one of them.
 */
public abstract class BasePrayerWidgetProvider : AppWidgetProvider() {

    /**
     * Draws this provider's layout. Called off the main thread.
     *
     * `internal` rather than `protected` because its parameters are: the provider classes have to
     * be public for the manifest to name them, but nothing outside this module has any business
     * calling this.
     */
    internal abstract fun buildViews(
        context: Context,
        content: WidgetContent,
        now: Instant,
        size: WidgetSize,
    ): RemoteViews

    /**
     * Distinguishes this provider's alarm from the other's.
     *
     * `PendingIntent` equality ignores the intent's extras but honours the request code, so two
     * providers sharing one would silently replace each other's alarm and only one widget would
     * ever be redrawn.
     */
    protected abstract val alarmRequestCode: Int

    override fun onReceive(context: Context, intent: Intent) {
        // Let AppWidgetProvider dispatch onUpdate/onEnabled/onDisabled first, so cleanup on the
        // last widget's removal happens before we would otherwise re-arm an alarm for nothing.
        super.onReceive(context, intent)

        if (intent.action !in REFRESH_ON) return
        refreshAsync(context)
    }

    /**
     * The launcher resized a widget, so the layout it should draw may have changed.
     *
     * This arrives as its own callback rather than through [onReceive]'s action gate —
     * `ACTION_APPWIDGET_OPTIONS_CHANGED` is delivered to the override without needing an
     * `<intent-filter>` entry, which is why it is deliberately absent from both [REFRESH_ON] and
     * the manifest.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        refreshAsync(context)
    }

    override fun onDisabled(context: Context) {
        // The last widget of this kind is gone, so its pending alarm has nothing left to redraw.
        alarmManager(context).cancel(tick(context))
    }

    private fun refreshAsync(context: Context) {
        // `calendar()` suspends and a receiver is not allowed to block. Without goAsync the process
        // can be killed mid-read and the widget renders stale or blank — intermittently, which is
        // the worst way for it to fail.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            // Deliberately catching everything. This runs on a broadcast, where an escaping
            // exception takes down the whole app process and the launcher replaces the widget with
            // "Problem loading" — a worse outcome, for the user, than one stale redraw. There is
            // nothing here to retry against either: the next alarm or sync will call this again.
            @Suppress("TooGenericExceptionCaught")
            try {
                render(context)
            } catch (e: Exception) {
                Log.w(TAG, "Widget refresh failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun render(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, javaClass))
        if (ids.isEmpty()) return

        // The widget is inflated by the launcher, so it gets the *system* locale rather than the
        // app's chosen one — LanguageOverride.attach only runs when an activity attaches. Wrapping
        // here is what stops a user reading the app in Arabic from getting an English widget.
        val localised = LanguageOverride.wrap(context)

        val preferences = PreferencesRepository(context)
        val repository = PrayerScheduleRepository(
            dao = ScheduleStore.dao(context),
            preferences = preferences,
        )

        val now = Instant.now()
        val today = LocalDate.now()
        val content = widgetContentFor(
            calendar = repository.timelineCalendar(today),
            now = now,
            date = today,
            locationLabel = preferences.current().location.effectiveLocation?.label,
        )

        // Per widget, because two copies of the same widget can be different sizes.
        for (id in ids) {
            manager.updateAppWidget(id, buildViews(localised, content, now, manager.sizeOf(id)))
        }

        schedule(context, content.validUntil)
    }

    /** Arms the single alarm that redraws this provider's widgets, replacing any already pending. */
    private fun schedule(context: Context, at: Instant?) {
        val alarms = alarmManager(context)
        val intent = tick(context)

        if (at == null) {
            // Nothing cached to wait for. Try again in an hour rather than never — a sync may land
            // in the meantime, and a widget that gives up permanently reads as broken.
            alarms.set(AlarmManager.RTC, System.currentTimeMillis() + RETRY_MILLIS, intent)
            return
        }

        alarms.set(AlarmManager.RTC, at.toEpochMilli(), intent)
    }

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    private fun tick(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        alarmRequestCode,
        Intent(context, javaClass).setAction(ACTION_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    public companion object {
        /** Our own alarm, distinct from the platform's widget-update broadcast. */
        public const val ACTION_TICK: String = "com.salahwatch.app.widget.TICK"

        /**
         * Everything that can invalidate what a widget is showing.
         *
         * The last four are not optional. Alarms do not survive a reboot or an app update, and a
         * widget whose alarm was dropped sits on the home screen counting down to a prayer that
         * happened yesterday — with nothing to tell the user it has stopped. A time or timezone
         * change moves every prayer on the face of it.
         *
         * `ACTION_APPWIDGET_OPTIONS_CHANGED` is deliberately **not** here: it is delivered straight
         * to [onAppWidgetOptionsChanged] and needs no filter entry.
         */
        public val REFRESH_ON: Set<String> = setOf(
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            ACTION_TICK,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            PrayerSyncWorker.ACTION_TIMES_CHANGED,
        )

        /** How long to wait before looking again when there is nothing cached at all. */
        private const val RETRY_MILLIS = 60 * 60 * 1000L

        private const val TAG = "PrayerWidget"

        /** Redraws every placed widget of both kinds. Called after a sync or a settings change. */
        public fun refreshAll(context: Context) {
            for (provider in listOf(
                PrayerWidgetProvider::class.java,
                PrayerStripWidgetProvider::class.java,
            )) {
                context.sendBroadcast(Intent(context, provider).setAction(ACTION_TICK))
            }
        }
    }
}

/** The space the launcher has given widget [id], as [WidgetSize] reads those four values. */
private fun AppWidgetManager.sizeOf(id: Int): WidgetSize {
    val options = getAppWidgetOptions(id)
    return WidgetSize.of(
        minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
        maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
        minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
        maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
    )
}
