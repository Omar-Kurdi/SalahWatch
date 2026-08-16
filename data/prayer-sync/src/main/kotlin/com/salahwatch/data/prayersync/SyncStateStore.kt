package com.salahwatch.data.prayersync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.salahwatch.data.prayertimes.ScheduleCacheKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.syncStore: DataStore<Preferences> by preferencesDataStore(name = "prayer_sync")

/**
 * Remembers what the cached schedule was built for.
 *
 * Without this the cache is silently wrong after any change that alters prayer times — moving city,
 * switching calculation method, changing school. Room only stores *when* each prayer is, not what
 * assumptions produced it, so the answer has to be recorded alongside.
 *
 * Stored as a flat string rather than structured fields because nothing ever reads the parts: the
 * only question asked is "does this still match?", and equality on the whole key answers it.
 */
public class SyncStateStore(private val context: Context) : SyncState {

    /** The key the cache was populated under, or null if nothing has ever been fetched. */
    public suspend fun currentKey(): String? =
        context.syncStore.data.map { it[KEY] }.first()

    override suspend fun setKey(key: ScheduleCacheKey) {
        context.syncStore.edit { it[KEY] = key.asString() }
    }

    public suspend fun clear() {
        context.syncStore.edit { it.remove(KEY) }
    }

    /**
     * Whether cached schedules are still valid for [key].
     *
     * Returns false when nothing has been fetched yet, which correctly routes a fresh install into
     * a full sync.
     */
    override suspend fun matches(key: ScheduleCacheKey): Boolean =
        currentKey() == key.asString()

    private companion object {
        val KEY = stringPreferencesKey("schedule_cache_key")
    }
}

/**
 * A stable string form of the cache key.
 *
 * Coordinates are rounded to three decimals — roughly 100 m — so that GPS jitter does not
 * invalidate a year of cached data every time a fix wobbles. Prayer times do not change measurably
 * over that distance, but a naive full-precision comparison would refetch constantly.
 */
internal fun ScheduleCacheKey.asString(): String = listOf(
    "%.3f".format(latitude),
    "%.3f".format(longitude),
    zoneId,
    methodId.toString(),
    school,
    source,
).joinToString("|")
