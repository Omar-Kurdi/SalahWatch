package com.salahwatch.data.prayersync

import com.salahwatch.core.model.CalculationSettings
import com.salahwatch.core.model.DaySchedule
import com.salahwatch.core.model.PrayerLocation
import com.salahwatch.core.preferences.UserPreferences
import com.salahwatch.data.prayertimes.ScheduleCacheKey
import java.time.LocalDate

/**
 * The seams [PrayerSyncer] works against.
 *
 * These exist so the sync *policy* — when to refetch, what invalidates a cache, how far ahead to
 * reach — can be tested on a plain JVM with no Android context, no database and no network. That
 * policy is where the subtle failures live: a cache silently kept after the user moves, or a refetch
 * on every wake-up. Testing it through Room and OkHttp would be slower and would prove less.
 */
public interface PrayerTimeSource {
    /** Every day of [year] for [location]. Throws [java.io.IOException] on failure. */
    public suspend fun annualCalendar(
        location: PrayerLocation,
        year: Int,
        settings: CalculationSettings,
    ): List<DaySchedule>
}


/** Records what assumptions the cached schedule was built under. */
public interface SyncState {
    public suspend fun matches(key: ScheduleCacheKey): Boolean
    public suspend fun setKey(key: ScheduleCacheKey)
}

/**
 * Reads a chosen mosque's published year.
 *
 * Returns null when the mosque cannot be reached or its data fails validation, so the syncer can
 * fall back to calculation rather than leaving the user with nothing.
 */
public interface MosqueTimeSource {
    public suspend fun calendarFor(
        mosque: com.salahwatch.core.model.MosqueRef,
        location: PrayerLocation,
    ): List<DaySchedule>?
}

/**
 * An area's bundled official timetable.
 *
 * Returns every day the bundle holds, which is generally *not* the range the caller wanted: these
 * files end on the last day the authority had published. Callers fill the remainder themselves.
 */
public interface OfficialTimeSource {
    public suspend fun calendarFor(
        area: com.salahwatch.core.model.OfficialArea,
    ): List<DaySchedule>?
}

/** Supplies current user settings. A function rather than a repository so tests need no DataStore. */
public fun interface PreferencesSource {
    public suspend fun current(): UserPreferences
}
