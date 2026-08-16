package com.salahwatch.wear.complications

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingTimelineComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.TimeInterval
import androidx.wear.watchface.complications.datasource.TimelineEntry
import com.salahwatch.core.calculation.ComplicationMode
import com.salahwatch.core.calculation.PhaseContent
import com.salahwatch.core.calculation.PhaseTimelineBuilder
import com.salahwatch.core.calculation.PrayerPhase
import com.salahwatch.core.calculation.TimeKind
import com.salahwatch.core.database.ScheduleStore
import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.ScheduleSource
import com.salahwatch.core.preferences.LanguageOverride
import com.salahwatch.core.preferences.PreferencesRepository
import com.salahwatch.data.prayertimes.PrayerScheduleRepository
import com.salahwatch.wear.MainActivity
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Every complication in this app, as a **timeline**.
 *
 * This is the one structural decision that matters here, and it was learned the hard way. A
 * complication service can answer "what should I show right now" and wait to be asked again — but
 * with `UPDATE_PERIOD_SECONDS=0` it is never asked again, so it keeps displaying the prayer that was
 * next when the user added it. Eleven of the twelve providers this replaces worked that way, and
 * "Current Prayer is broken" was the user noticing it.
 *
 * A timeline provider instead hands the platform *every transition for the next two days in one
 * response*: when a clock time becomes a countdown, when a countdown becomes an Iqama countdown,
 * when that becomes elapsed time, and when the cycle restarts on the following prayer. The
 * consequence is that this process does not run between transitions at all — no alarms, no polling,
 * no per-minute wakeups. Rollover is not a thing that has to happen; it is already published.
 *
 * The five subclasses differ only in their [ComplicationMode]. Each is a separate entry in the
 * watch face picker because each is a genuinely different thing to look at, and a picker cannot
 * expose a preference.
 */
internal abstract class PhaseTimelineComplicationService(
    private val mode: ComplicationMode,
) : SuspendingTimelineComplicationDataSourceService() {

    private val repository: PrayerScheduleRepository by lazy {
        PrayerScheduleRepository(
            dao = ScheduleStore.dao(applicationContext),
            preferences = PreferencesRepository(applicationContext),
        )
    }

    private val builder = PhaseTimelineBuilder()

    /**
     * Translated words, resolved through [LanguageOverride].
     *
     * A service has no activity to attach a locale to, so reading straight from the application
     * context would leave complications in the watch's system language while the app around them
     * followed the language chosen on the phone.
     */
    private val labels: ComplicationLabels by lazy {
        ComplicationLabels.from(LanguageOverride.wrap(applicationContext))
    }

    protected open val clock: Clock get() = Clock.systemDefaultZone()

    final override suspend fun onComplicationRequest(
        request: ComplicationRequest,
    ): ComplicationDataTimeline? {
        if (request.complicationType !in SUPPORTED) return null

        val now = Instant.now(clock)
        val calendar = withContext(Dispatchers.IO) {
            repository.timelineCalendar(LocalDate.now(clock))
        }

        val fallback = ComplicationRenderer.noData(request.complicationType, tapAction())
            ?: return null

        // No special case for an empty calendar: the builder already yields no phases for one, and
        // a timeline with no entries falls back to `defaultComplicationData` — which is exactly the
        // "no data" placeholder we want shown.
        val phases = builder.build(
            calendar = calendar,
            mode = mode,
            from = now,
            until = now.plus(TIMELINE_HORIZON),
        )

        val zone = calendar.occurrences.firstOrNull()?.zone ?: ZoneId.systemDefault()

        return ComplicationDataTimeline(
            defaultComplicationData = fallback,
            timelineEntries = phases
                .withCountdownGranularity()
                .mapNotNull { it.toTimelineEntry(request.complicationType, zone) },
        )
    }

    /**
     * One rendered phase becomes one timeline entry.
     *
     * Phases from [PhaseTimelineBuilder] never overlap, so the platform's "shortest entry wins"
     * tie-break is never exercised and the rendered behaviour is exactly what the phase list says.
     */
    private fun RenderedPhase.toTimelineEntry(
        type: ComplicationType,
        zone: ZoneId,
    ): TimelineEntry? {
        val data = ComplicationRenderer.forType(type, this, zone, tapAction(), labels) ?: return null
        return TimelineEntry(TimeInterval(phase.start, phase.end), data)
    }

    /**
     * Sample data for the complication picker.
     *
     * The picker renders this before the provider has been chosen, often before any schedule
     * exists, so it must never touch the database — a preview showing "--:--" makes a provider look
     * broken in the picker. Each mode previews the phase that best characterises it.
     */
    final override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type !in SUPPORTED) return null
        return ComplicationRenderer.forType(
            type = type,
            // Minutes, always: a preview showing seconds would tick in the picker.
            rendered = RenderedPhase(previewPhase(mode), ComplicationTexts.Granularity.MINUTES),
            zone = ZoneId.systemDefault(),
            tapAction = null,
        )
    }

    /** Tapping any complication opens the watch app. */
    private fun tapAction(): PendingIntent? = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        val SUPPORTED = setOf(ComplicationType.SHORT_TEXT, ComplicationType.RANGED_VALUE)

        /**
         * How far ahead to publish.
         *
         * Two days rather than one so a device that fails to refresh — flat battery overnight, no
         * phone contact, aeroplane mode — still renders correct transitions the next day instead of
         * falling back to the placeholder.
         */
        val TIMELINE_HORIZON: Duration = Duration.ofDays(2)
    }
}

/** **(a) Pray Time** — the next prayer and the time its Adhan is called, e.g. `Asr / 3:50`. */
internal class PrayTimeComplicationService :
    PhaseTimelineComplicationService(ComplicationMode.PRAY_TIME)

/** **(b) Adhan Time/Countdown** — the time, becoming a countdown ten minutes out, then the next. */
internal class AdhanTimeCountdownComplicationService :
    PhaseTimelineComplicationService(ComplicationMode.ADHAN_TIME_THEN_COUNTDOWN)

/**
 * **(c) Prayer Progress** — the full cycle.
 *
 * Time, then a countdown from an hour out, then a countdown to the Iqama, then `Pray 3:12` counting
 * up for five minutes, then the next prayer. Both countdowns switch to `mm:ss` in their final five
 * minutes; an Iqama countdown is usually shorter than that and so reads as `mm:ss` throughout.
 */
internal class PrayerProgressComplicationService :
    PhaseTimelineComplicationService(ComplicationMode.PRAYER_PROGRESS)

/** **(d) Adhan Countdown** — always a countdown to the next Adhan. */
internal class AdhanCountdownComplicationService :
    PhaseTimelineComplicationService(ComplicationMode.ADHAN_COUNTDOWN)

/** **(e) Adhan / Iqama Countdown** — a countdown to the Adhan, then one to its Iqama. */
internal class AdhanIqamaCountdownComplicationService :
    PhaseTimelineComplicationService(ComplicationMode.ADHAN_THEN_IQAMA_COUNTDOWN)

/** A plausible phase for the picker, characteristic of [mode]. */
private fun previewPhase(mode: ComplicationMode): PrayerPhase {
    val now = Instant.now()
    val adhan = now.plus(Duration.ofMinutes(PREVIEW_MINUTES))

    val content = when (mode) {
        ComplicationMode.PRAY_TIME -> PhaseContent.AbsoluteTime(adhan, TimeKind.ADHAN)
        ComplicationMode.ADHAN_TIME_THEN_COUNTDOWN -> PhaseContent.AbsoluteTime(adhan, TimeKind.ADHAN)
        ComplicationMode.PRAYER_PROGRESS,
        ComplicationMode.ADHAN_COUNTDOWN,
        ComplicationMode.ADHAN_THEN_IQAMA_COUNTDOWN,
        -> PhaseContent.CountDown(adhan, TimeKind.ADHAN)
    }

    return PrayerPhase(
        start = now,
        end = adhan,
        prayer = Prayer.ASR,
        content = content,
        source = ScheduleSource.ALADHAN,
        arcFrom = now.minus(Duration.ofMinutes(PREVIEW_ELAPSED_MINUTES)),
        arcTo = adhan,
    )
}

private const val PREVIEW_MINUTES = 17L
private const val PREVIEW_ELAPSED_MINUTES = 43L
