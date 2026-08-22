package com.salahwatch.data.prayersync

import android.util.Log
import com.salahwatch.core.model.DaySchedule
import com.salahwatch.core.model.PrayerLocation
import com.salahwatch.core.model.TimeSource
import com.salahwatch.core.model.effectiveLocation
import com.salahwatch.core.preferences.UserPreferences
import com.salahwatch.data.prayertimes.ScheduleCache
import com.salahwatch.data.prayertimes.ScheduleCacheKey
import java.io.IOException
import java.time.Clock
import java.time.LocalDate

/** What a sync attempt did, so callers can decide whether to retry and what to log. */
public sealed interface SyncResult {
    /** Schedules were fetched and cached. */
    public data class Fetched(val days: Int, val years: List<Int>) : SyncResult

    /** Nothing needed doing — the cache already covers the horizon under the current settings. */
    public data object AlreadyCurrent : SyncResult

    /** No location configured yet, so there is nothing to fetch. Not an error, not retryable. */
    public data object NoLocation : SyncResult

    /** The network or API failed. Retryable. */
    public data class Failed(val cause: Throwable) : SyncResult
}

/**
 * Brings the local cache up to date from AlAdhan.
 *
 * The strategy is deliberately coarse: fetch a **whole year at a time**. A year is ~500 KB and about
 * a second on the wire, and it means the app then works offline indefinitely — which makes SPEC.md's
 * offline requirement a property of the design rather than a fallback path to be tested into
 * existence.
 *
 * It refetches only when it must: when location or calculation settings change (detected through
 * [SyncState]), or when cached data is running out. A daily wake-up that finds nothing to do returns
 * [SyncResult.AlreadyCurrent] without touching the network.
 */
public class PrayerSyncer(
    private val source: PrayerTimeSource,
    private val mosqueSource: MosqueTimeSource? = null,
    private val cache: ScheduleCache,
    private val syncState: SyncState,
    private val preferences: PreferencesSource,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    /**
     * Ensures the cache covers the next [MINIMUM_HORIZON_DAYS] days.
     *
     * @param force refetch even when the cache looks adequate — used when the user changes a setting
     *   that alters times and expects to see the effect immediately.
     */
    public suspend fun sync(force: Boolean = false): SyncResult {
        val prefs = preferences.current()
        val location = prefs.location.effectiveLocation ?: return SyncResult.NoLocation
        val key = ScheduleCacheKey.of(prefs, location)
        val today = LocalDate.now(clock)

        // A settings or location change invalidates every cached day, not just stale ones: the times
        // themselves differ, so keeping any would interleave two schedules.
        val keyMatches = syncState.matches(key)
        if (!keyMatches) cache.clear()

        val upToDate = keyMatches && !force && hasEnoughCached(today)

        return if (upToDate) {
            cache.prune(today.minusDays(RETAIN_PAST_DAYS))
            SyncResult.AlreadyCurrent
        } else {
            try {
                fetchAndStore(location, prefs, today, key)
            } catch (e: IOException) {
                Log.w(TAG, "Prayer time sync failed; will retry", e)
                SyncResult.Failed(e)
            }
        }
    }

    private suspend fun fetchAndStore(
        location: PrayerLocation,
        prefs: UserPreferences,
        today: LocalDate,
        key: ScheduleCacheKey,
    ): SyncResult {
        // This year, plus next year once the horizon crosses into it, so the turn of the year is not
        // a cliff the app falls off on 31 December.
        val years = buildList {
            add(today.year)
            val horizon = today.plusDays(MINIMUM_HORIZON_DAYS)
            if (horizon.year != today.year) add(horizon.year)
        }

        // A mosque publishes its whole year in one document, so there is no per-year loop and no
        // partial state to reconcile: either the mosque's calendar is usable or the sync falls back
        // to calculation, which is what the caller already handles.
        val fetched = when (prefs.timeSource) {
            TimeSource.NEARBY_MOSQUE -> {
                val mosque = prefs.mosque
                val fromMosque = if (mosque != null && mosqueSource != null) {
                    mosqueSource.calendarFor(mosque, location)
                } else {
                    null
                }
                fromMosque ?: fallbackToCalculation(location, prefs, years)
            }

            TimeSource.CALCULATED -> fallbackToCalculation(location, prefs, years)
        }

        cache.store(fetched)
        syncState.setKey(key)
        cache.prune(today.minusDays(RETAIN_PAST_DAYS))

        return SyncResult.Fetched(days = fetched.size, years = years)
    }

    /**
     * Astronomical times for the configured method.
     *
     * Also the fallback when a mosque cannot be reached or its published data fails validation —
     * showing approximate times is better than showing none, and they are still marked by source.
     */
    private suspend fun fallbackToCalculation(
        location: PrayerLocation,
        prefs: UserPreferences,
        years: List<Int>,
    ): List<DaySchedule> = buildList {
        for (year in years) {
            addAll(source.annualCalendar(location, year, prefs.calculation))
        }
    }

    private suspend fun hasEnoughCached(today: LocalDate): Boolean {
        val window = MINIMUM_HORIZON_DAYS.toInt()
        return cache.cachedDays(today, window) >= window
    }

    public companion object {
        /**
         * How far ahead the cache must reach before a refetch is skipped.
         *
         * Sixty days rather than a handful: fetch granularity is a whole year, so cutting it fine
         * saves nothing, and a wide margin means a device offline for weeks — or one whose
         * background work the system keeps deferring — still has correct times.
         */
        public const val MINIMUM_HORIZON_DAYS: Long = 60

        /**
         * How much history survives a prune.
         *
         * Two days would be enough for correctness alone — yesterday is needed so a late Isha
         * Iqama crossing midnight is still resolvable. It is a month because the phone's date
         * picker browses that far back, and a pruned day is silently recalculated on read: the
         * user would be shown a fresh astronomical estimate of a day whose real, published times
         * we already had. Keeping them costs one row per day.
         */
        public const val RETAIN_PAST_DAYS: Long = 35

        private const val TAG = "PrayerSyncer"
    }
}
