package com.salahwatch.wear.complications

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

/**
 * Asks the system to re-request data from every provider in this app.
 *
 * Used sparingly and only on events that genuinely invalidate what is displayed — a new day's
 * schedule syncing, the clock or timezone changing, a reboot. Google's guidance is not to call this
 * more than once per five minutes on average; in practice this app calls it about once a day.
 *
 * It is **not** called when a prayer arrives. The timeline already contains every transition, so the
 * platform switches entries by itself.
 */
internal object ComplicationRefresher {

    /**
     * Every provider this app declares.
     *
     * Listed explicitly rather than discovered by querying the package manager: a manifest query on
     * each refresh would be slower and could silently pick up nothing if the intent filter changed.
     * A missing entry here shows up as a stale complication, which is easy to spot in testing.
     */
    private val services: List<Class<*>> = listOf(
        PrayTimeComplicationService::class.java,
        AdhanTimeCountdownComplicationService::class.java,
        PrayerProgressComplicationService::class.java,
        AdhanCountdownComplicationService::class.java,
        AdhanIqamaCountdownComplicationService::class.java,
    )

    fun refreshAll(context: Context) {
        for (service in services) {
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, service))
                .requestUpdateAll()
        }
    }
}
