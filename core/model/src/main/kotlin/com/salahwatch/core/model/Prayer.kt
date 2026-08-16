package com.salahwatch.core.model

import kotlinx.serialization.Serializable

/**
 * Mirrors `androidx.wear.watchface.complications.data.ShortTextComplicationData.MAX_TEXT_LENGTH`.
 *
 * Declared at file level rather than in [Prayer.Companion] because enum entries are constructed
 * before the companion object is initialized, and [Prayer]'s `init` block asserts against it.
 */
private const val SHORT_TEXT_BUDGET = 7

/**
 * The six daily time markers reported by the calculation sources.
 *
 * [SUNRISE] is included because it appears in the day's schedule and gets its own complication
 * (SPEC §8), but it is **not** a prayer: it never becomes the "next prayer", it has no Iqama, and
 * it never ends the current prayer window. Use [isPrayer] rather than testing for it inline.
 *
 * [shortName] is guaranteed to fit the 7-character `SHORT_TEXT` budget enforced by
 * `ShortTextComplicationData.MAX_TEXT_LENGTH`. `MAGHRIB` is exactly 7 characters, which is why no
 * abbreviation scheme is needed.
 */
@Serializable
public enum class Prayer(public val shortName: String) {
    FAJR("FAJR"),
    SUNRISE("SUNRISE"),
    DHUHR("DHUHR"),
    ASR("ASR"),
    MAGHRIB("MAGHRIB"),
    ISHA("ISHA"),
    ;

    /** True for the five obligatory prayers; false for [SUNRISE]. */
    public val isPrayer: Boolean get() = this != SUNRISE

    init {
        require(shortName.length <= SHORT_TEXT_BUDGET) {
            "shortName '$shortName' exceeds the SHORT_TEXT complication budget"
        }
    }

    public companion object {
        /** Mirrors `androidx.wear.watchface.complications.data.ShortTextComplicationData.MAX_TEXT_LENGTH`. */
        public const val MAX_SHORT_TEXT_LENGTH: Int = SHORT_TEXT_BUDGET

        /** The five obligatory prayers, in daily order. Excludes [SUNRISE]. */
        public val obligatory: List<Prayer> = entries.filter { it.isPrayer }
    }
}
