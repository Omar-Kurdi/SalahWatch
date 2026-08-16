package com.salahwatch.core.database

import android.content.Context

/**
 * The module's public entry point.
 *
 * Deliberately narrow: it hands back a [ScheduleDao] and nothing else. The Room `@Database` class
 * itself stays internal so that Room does not leak onto consumers' compile classpaths — a module
 * that wants prayer times should not end up depending on a persistence library, and keeping the
 * boundary here means swapping the storage engine later touches only this module.
 */
public object ScheduleStore {
    public fun dao(context: Context): ScheduleDao =
        PrayerDatabase.get(context).scheduleDao()
}
