package com.salahwatch.wear.complications

import android.content.Context
import com.salahwatch.core.model.Prayer
import com.salahwatch.wear.R

/**
 * The words a complication shows, resolved once from resources.
 *
 * This exists so the text-building logic can stay free of Android types and remain unit-testable,
 * while still being translated. `Prayer.shortName` is an ASCII identifier sized for the seven
 * character `SHORT_TEXT` budget, not something to show a reader — the Arabic names are read from
 * resources here instead.
 *
 * Arabic prayer names all fit the same budget: الفجر (5), الشروق (6), الظهر (5), العصر (5),
 * المغرب (6), العشاء (6).
 */
internal data class ComplicationLabels(
    val prayerNames: Map<Prayer, String>,
    val adhan: String,
    val iqama: String,
    /** The title over the incremental timer shown for a few minutes after the Iqama. */
    val pray: String,
    val estimated: String,
) {
    fun nameOf(prayer: Prayer): String = prayerNames[prayer] ?: prayer.shortName

    companion object {
        fun from(context: Context): ComplicationLabels = ComplicationLabels(
            prayerNames = mapOf(
                Prayer.FAJR to context.getString(R.string.prayer_fajr),
                Prayer.SUNRISE to context.getString(R.string.prayer_sunrise),
                Prayer.DHUHR to context.getString(R.string.prayer_dhuhr),
                Prayer.ASR to context.getString(R.string.prayer_asr),
                Prayer.MAGHRIB to context.getString(R.string.prayer_maghrib),
                Prayer.ISHA to context.getString(R.string.prayer_isha),
            ),
            adhan = context.getString(R.string.label_adhan),
            iqama = context.getString(R.string.label_iqama),
            pray = context.getString(R.string.label_pray),
            estimated = context.getString(R.string.label_estimated),
        )

        /** Untranslated, for previews and tests where no context is available. */
        val Fallback: ComplicationLabels = ComplicationLabels(
            prayerNames = Prayer.entries.associateWith { it.shortName },
            adhan = "Adhan",
            iqama = "Iqama",
            pray = "Pray",
            estimated = "estimated",
        )
    }
}
