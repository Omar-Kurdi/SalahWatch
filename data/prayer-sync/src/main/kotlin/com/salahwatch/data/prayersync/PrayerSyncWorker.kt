package com.salahwatch.data.prayersync

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.salahwatch.core.database.ScheduleStore
import com.salahwatch.core.preferences.PreferencesRepository
import com.salahwatch.data.prayertimes.PrayerScheduleRepository
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Refreshes the cached prayer schedule in the background.
 *
 * This is what makes SPEC.md's "Do NOT rely on the phone app being opened" true. The work is
 * deferrable and network-constrained — it has no deadline, because the cache already holds months of
 * data, so there is never a moment where a delayed sync leaves the user without prayer times.
 */
public class PrayerSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val preferences = PreferencesRepository(applicationContext)
        val syncer = PrayerSyncer(
            source = AlAdhanClient(),
            mosqueSource = MawaqitTimeSource(),
            officialSource = BundledOfficialSource(applicationContext),
            cache = PrayerScheduleRepository(
                dao = ScheduleStore.dao(applicationContext),
                preferences = preferences,
            ),
            syncState = SyncStateStore(applicationContext),
            preferences = { preferences.current() },
        )

        return when (val result = syncer.sync(force = inputData.getBoolean(KEY_FORCE, false))) {
            is SyncResult.Fetched -> {
                Log.i(TAG, "Cached ${result.days} days for ${result.years}")
                announceNewTimes()
                Result.success()
            }

            SyncResult.AlreadyCurrent -> Result.success()

            // Nothing to fetch without a location. Retrying would burn wakeups for no reason; the
            // sync is re-triggered explicitly when a location is set.
            SyncResult.NoLocation -> Result.success()

            is SyncResult.Failed -> Result.retry()
        }
    }

    /**
     * Tells the rest of the app that the cache now holds different times.
     *
     * Sent only when a fetch actually changed something. The phone widget is the reason this
     * exists: it is drawn by the launcher and observes nothing, so a sync landing while the app is
     * closed would otherwise leave it showing the previous source\'s times until its next scheduled
     * redraw — which, mid-afternoon, can be hours away.
     *
     * Package-scoped rather than implicit, because an implicit broadcast would not reach a
     * manifest-declared receiver at all from API 26 onwards.
     */
    private fun announceNewTimes() {
        applicationContext.sendBroadcast(
            Intent(ACTION_TIMES_CHANGED).setPackage(applicationContext.packageName),
        )
    }

    public companion object {
        /** Broadcast when a sync has replaced the cached times. See [announceNewTimes]. */
        public const val ACTION_TIMES_CHANGED: String = "com.salahwatch.data.prayersync.TIMES_CHANGED"

        private const val TAG = "PrayerSyncWorker"
        private const val KEY_FORCE = "force"

        internal const val PERIODIC_WORK = "prayer-sync-periodic"
        internal const val ONE_SHOT_WORK = "prayer-sync-now"
    }
}

/**
 * Schedules [PrayerSyncWorker].
 *
 * Two triggers, deliberately:
 *
 * - a **daily periodic** job, which covers SPEC.md's "DAILY RESET" — it tops up the horizon, prunes
 *   old days, and keeps working whether or not the app is ever opened;
 * - an **immediate one-shot**, used when the user changes something that alters prayer times and
 *   reasonably expects to see it take effect.
 *
 * Notably absent: any alarm tied to a prayer time. The complications already hold a timeline of
 * every transition, so nothing needs to wake up when a prayer arrives.
 */
public object PrayerSyncScheduler {

    /** Called on app start; safe to call repeatedly. */
    public fun ensureScheduled(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PrayerSyncWorker.PERIODIC_WORK,
            // KEEP, so an already-scheduled job is not reset on every launch — replacing it each
            // time would push the next run a day out and it might never actually fire.
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PrayerSyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .build(),
        )
    }

    /** Requests a refresh now — after a settings or location change. */
    public fun syncNow(context: Context, force: Boolean = true) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            PrayerSyncWorker.ONE_SHOT_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PrayerSyncWorker>()
                .setConstraints(networkConstraints())
                .setInputData(androidx.work.workDataOf("force" to force))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .build(),
        )
    }

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Long enough that a flaky connection does not retry in a tight loop. */
    private val BACKOFF_MINUTES = Duration.ofMinutes(RETRY_BACKOFF_MINUTES).toMinutes()

    private const val RETRY_BACKOFF_MINUTES = 15L
}
