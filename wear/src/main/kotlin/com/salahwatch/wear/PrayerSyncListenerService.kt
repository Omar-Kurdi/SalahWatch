package com.salahwatch.wear

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.salahwatch.core.database.ScheduleStore
import com.salahwatch.core.preferences.LanguageOverride
import com.salahwatch.core.preferences.PreferencesRepository
import com.salahwatch.core.preferences.UserPreferences
import com.salahwatch.data.prayertimes.PrayerScheduleRepository
import com.salahwatch.data.sync.ScheduleCodec
import com.salahwatch.data.sync.SyncPaths
import com.salahwatch.wear.complications.ComplicationRefresher
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Receives prayer data pushed from the phone and writes it into the watch's own store.
 *
 * The watch keeps a full local copy rather than reading through to the phone on demand. That is what
 * makes SPEC.md's "the watch should still show today's prayer information if the phone is
 * unavailable" true by construction: after one successful sync there is nothing left to ask for.
 *
 * Complications are refreshed once at the end of a batch, not per data item, so a phone that pushes
 * schedule and preferences together causes one refresh rather than two.
 */
internal class PrayerSyncListenerService : WearableListenerService() {

    private val json = Json { ignoreUnknownKeys = true }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        var changed = false

        dataEvents.use { events ->
            for (event in events) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                val item = event.dataItem
                val dataMap = DataMapItem.fromDataItem(item).dataMap

                changed = when (item.uri.path) {
                    SyncPaths.SCHEDULE -> applySchedule(dataMap.getByteArray(SyncPaths.KEY_SCHEDULE)) || changed
                    SyncPaths.PREFERENCES -> applyPreferences(dataMap.getString(SyncPaths.KEY_PREFERENCES)) || changed
                    else -> changed
                }
            }
        }

        if (changed) {
            ComplicationRefresher.refreshAll(this)
        }
    }

    /**
     * `runBlocking` is correct here, unusually.
     *
     * [WearableListenerService] callbacks run on a background thread and the service may be torn
     * down as soon as `onDataChanged` returns, so launching into a scope would risk the write being
     * cancelled mid-flight. Blocking this already-background thread is what keeps the write atomic
     * with respect to the callback's lifetime.
     */
    private fun applySchedule(payload: ByteArray?): Boolean {
        val schedules = payload?.let(ScheduleCodec::decode)
        if (schedules.isNullOrEmpty()) {
            Log.w(TAG, "Ignoring unreadable schedule payload; keeping existing data")
            return false
        }

        return runCatching {
            runBlocking {
                PrayerScheduleRepository(
                    dao = ScheduleStore.dao(applicationContext),
                    preferences = PreferencesRepository(applicationContext),
                ).store(schedules)
            }
            Log.i(TAG, "Stored ${schedules.size} days from the phone")
            true
        }.getOrElse {
            Log.e(TAG, "Failed to store synced schedule", it)
            false
        }
    }

    private fun applyPreferences(payload: String?): Boolean {
        if (payload == null) return false

        return try {
            val preferences = json.decodeFromString<UserPreferences>(payload)
            runBlocking { PreferencesRepository(applicationContext).replace(preferences) }
            // The language is chosen on the phone but has to be resolved on the watch, where the
            // complications and the watch app read their own resources. Mirroring it here is what
            // lets a complication rendered before the watch app is ever opened come out in Arabic.
            LanguageOverride.select(applicationContext, preferences.language)
            true
        } catch (e: SerializationException) {
            // A newer phone build may send fields this watch does not understand. Keeping the
            // existing settings is better than clearing them.
            Log.w(TAG, "Ignoring unreadable preferences payload", e)
            false
        }
    }

    private companion object {
        const val TAG = "PrayerSyncListener"
    }
}
