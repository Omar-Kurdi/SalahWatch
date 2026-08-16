package com.salahwatch.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.salahwatch.wear.complications.ComplicationRefresher

/**
 * Re-renders complications after an event that invalidates what they show.
 *
 * SPEC.md calls out timezone changes, travel, a manually changed device clock, DST, and date changes
 * while offline. All of them share one property: the *stored* prayer times are still correct
 * absolute instants, but which one is "next" may have changed, so the complications need re-asking.
 *
 * The three actions handled here are exactly those that survive Android's implicit-broadcast
 * restrictions for manifest-declared receivers. `ACTION_DATE_CHANGED` is **not** on the exemption
 * list and deliberately is not relied on — the daily rollover is covered instead by the timeline,
 * which already spans 48 hours.
 *
 * This only re-renders; it never fetches. Refreshing is enough because the cached instants are still
 * correct — a clock or timezone change alters which prayer is *next*, not when any of them occur.
 */
internal class TimeChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            -> ComplicationRefresher.refreshAll(context)
        }
    }
}
