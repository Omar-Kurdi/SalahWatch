package com.salahwatch.data.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.salahwatch.core.model.DaySchedule
import com.salahwatch.core.preferences.UserPreferences
import java.io.IOException
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

/** Data Layer paths and keys, shared by the phone publisher and the watch listener. */
public object SyncPaths {
    public const val SCHEDULE: String = "/prayer/schedule"
    public const val PREFERENCES: String = "/prayer/preferences"

    public const val KEY_SCHEDULE: String = "schedule"
    public const val KEY_PREFERENCES: String = "preferences"

    /**
     * Bumped on every publish so the Data Layer sees a changed item.
     *
     * Without it, republishing an identical schedule is a no-op — the Data Layer deduplicates by
     * content — and the watch would never be told to refresh after, say, a settings change that
     * happens to leave times unchanged.
     */
    public const val KEY_REVISION: String = "revision"
}

/**
 * Publishes prayer data from the phone to the watch.
 *
 * Only the phone calls this. The watch is a pure consumer: it holds a full local copy and can
 * recalculate offline, so it never needs to talk back.
 */
public class PrayerDataLayerPublisher(
    private val context: Context,
    private val json: Json = Json,
) {
    /**
     * Sends [schedules] and [preferences] to any connected watch.
     *
     * @param schedules must be non-empty and share a timezone. Trimmed to [MAX_SYNC_DAYS] because
     *   the payload limit is 100 KB — a bound the codec's tests pin explicitly.
     * @return true if both items were written. False means no Data Layer was reachable, which is
     *   normal on a phone with no watch paired and is not an error.
     */
    public suspend fun publish(
        schedules: List<DaySchedule>,
        preferences: UserPreferences,
    ): Boolean {
        if (schedules.isEmpty()) return false

        val trimmed = schedules.take(MAX_SYNC_DAYS)
        val client = Wearable.getDataClient(context)

        return try {
            val scheduleRequest = PutDataMapRequest.create(SyncPaths.SCHEDULE).apply {
                dataMap.putByteArray(SyncPaths.KEY_SCHEDULE, ScheduleCodec.encode(trimmed))
                dataMap.putLong(SyncPaths.KEY_REVISION, preferences.revision)
            }.asPutDataRequest().setUrgent()

            val preferencesRequest = PutDataMapRequest.create(SyncPaths.PREFERENCES).apply {
                dataMap.putString(SyncPaths.KEY_PREFERENCES, json.encodeToString(preferences))
                dataMap.putLong(SyncPaths.KEY_REVISION, preferences.revision)
            }.asPutDataRequest().setUrgent()

            client.putDataItem(scheduleRequest).await()
            client.putDataItem(preferencesRequest).await()
            true
        } catch (e: IOException) {
            Log.w(TAG, "Could not publish to the watch; it will use cached data", e)
            false
        } catch (e: com.google.android.gms.common.api.ApiException) {
            // Raised when Wearable services are absent — an emulator without Play services, or a
            // phone paired to nothing. The phone app is fully usable in that state.
            Log.i(TAG, "Wearable Data Layer unavailable", e)
            false
        }
    }

    public companion object {
        private const val TAG = "PrayerDataLayer"

        /**
         * Days sent to the watch in one payload.
         *
         * 120 days is about 6 KB — far inside the 100 KB `DataItem` limit — and means a watch that
         * loses contact with its phone for months still has correct times. `setUrgent()` is used
         * because the alternative is a sync the system may defer for up to 30 minutes, and a user
         * who has just changed a setting expects to see it.
         */
        public const val MAX_SYNC_DAYS: Int = 120
    }
}
