package com.salahwatch.core.model

import kotlinx.serialization.Serializable

/** Asr calculation school. Maps to AlAdhan's `school` parameter and adhan2's `Madhab`. */
@Serializable
public enum class School(public val apiValue: Int) {
    /** Earlier Asr. AlAdhan `school=0`. The default per SPEC.md. */
    SHAFI(0),

    /** Later Asr. AlAdhan `school=1`. */
    HANAFI(1),
}

/**
 * Twilight definition for the Moonsighting Committee method.
 *
 * Verified against the AlAdhan API and the adhan2 source: this parameter is **only** consulted when
 * the calculation method is Moonsighting Committee (AlAdhan method 15). For every other method —
 * including the default 16 and SPEC.md's method 3 — it has no effect on the result. The settings UI
 * must therefore present it as conditional on the method rather than as a free-standing choice.
 */
@Serializable
public enum class Shafaq(public val apiValue: String) {
    GENERAL("general"),
    AHMER("ahmer"),
    ABYAD("abyad"),
}

/**
 * Per-prayer manual corrections in minutes, applied on top of the calculated time (SPEC.md
 * §"Prayer time adjustments"). Distinct from the offsets a calculation method applies internally.
 */
@Serializable
public data class PrayerAdjustments(
    val fajr: Int = 0,
    val sunrise: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0,
) {
    public operator fun get(prayer: Prayer): Int = when (prayer) {
        Prayer.FAJR -> fajr
        Prayer.SUNRISE -> sunrise
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
    }

    public val isIdentity: Boolean
        get() = fajr == 0 && sunrise == 0 && dhuhr == 0 && asr == 0 && maghrib == 0 && isha == 0

    /**
     * [prayer]'s correction moved by [delta], held inside [MAX_MINUTES].
     *
     * Takes a delta rather than a total on purpose. The value a settings screen is displaying
     * arrives through a write to disk and back, so computing a new total from it loses every tap
     * that lands before the round trip completes — which is why tapping `+` five times used to move
     * the value by one. A delta applies to whatever is stored at the moment it is applied.
     */
    public fun movedBy(prayer: Prayer, delta: Int): PrayerAdjustments {
        val moved = (get(prayer) + delta).coerceIn(-MAX_MINUTES, MAX_MINUTES)
        return when (prayer) {
            Prayer.FAJR -> copy(fajr = moved)
            Prayer.SUNRISE -> copy(sunrise = moved)
            Prayer.DHUHR -> copy(dhuhr = moved)
            Prayer.ASR -> copy(asr = moved)
            Prayer.MAGHRIB -> copy(maghrib = moved)
            Prayer.ISHA -> copy(isha = moved)
        }
    }

    public companion object {
        /**
         * How far a single prayer may be corrected, either way.
         *
         * A correction exists to reconcile the app with a timetable on a wall, which is a
         * disagreement of minutes. Two hours is far beyond any real one and still leaves the
         * stepper unbounded in practice — but it stops a held finger from reordering the day into
         * a state that takes as many taps to escape as it took to reach.
         */
        public const val MAX_MINUTES: Int = 120
    }
}

/**
 * [schedule] with each Adhan shifted by its correction.
 *
 * Applied here, at the point the times are read, rather than baked into what is fetched and cached.
 * That is what makes corrections work identically whichever source the times came from: a mosque
 * publishes a fixed table that no request parameter can tune, so a correction sent to the API would
 * apply to calculated times and silently do nothing to a mosque's. It also means changing a
 * correction is immediate, instead of waiting on a refetch of the whole year.
 *
 * Iqama times are cleared rather than shifted, because they are derived from the Adhan and are
 * recomputed straight after this by
 * [com.salahwatch.core.calculation.IqamaCalculator]. Times are re-sorted afterwards:
 * corrections are per-prayer, so a large enough one could otherwise leave the day out of order and
 * violate [DaySchedule]'s own invariant.
 */
public fun DaySchedule.shiftedBy(adjustments: PrayerAdjustments): DaySchedule {
    if (adjustments.isIdentity) return this

    return copy(
        times = times
            .map { time ->
                time.copy(
                    adhan = time.adhan.plusSeconds(adjustments[time.prayer] * SECONDS_PER_MINUTE),
                    iqama = null,
                )
            }
            .sortedBy { it.adhan },
    )
}

private const val SECONDS_PER_MINUTE = 60L

/**
 * Everything that determines the Adhan times for a location.
 *
 * There is no universally correct calculation method — the right one is whichever the user's local
 * authority follows, and that varies by country. [methodId] therefore only picks a starting point;
 * all 23 are selectable in settings and the difference between them is material, up to several
 * minutes on Isha.
 */
@Serializable
public data class CalculationSettings(
    val methodId: Int = DEFAULT_METHOD_ID,
    val school: School = School.SHAFI,
    val shafaq: Shafaq = Shafaq.GENERAL,
    val adjustments: PrayerAdjustments = PrayerAdjustments(),
) {
    /** True when [shafaq] actually influences the calculation. */
    public val isShafaqRelevant: Boolean get() = methodId == MOONSIGHTING_METHOD_ID

    public companion object {
        /** AlAdhan method 16. Fajr/Isha 18.2°, with +3 min on Dhuhr, Maghrib and Sunset. */
        public const val DEFAULT_METHOD_ID: Int = 16

        /** AlAdhan method 3, "Muslim World League". Fajr 18°, Isha 17°. */
        public const val MUSLIM_WORLD_LEAGUE_METHOD_ID: Int = 3

        /** AlAdhan method 15. The only method for which [Shafaq] is meaningful. */
        public const val MOONSIGHTING_METHOD_ID: Int = 15
    }
}

/** How Iqama times are derived. Only [FIXED_OFFSET] is implemented; the rest are future work. */
@Serializable
public enum class IqamaMode {
    /** Iqama = Adhan + a per-prayer offset in minutes. The shipping behaviour. */
    FIXED_OFFSET,

    /**
     * The offsets published by the chosen mosque.
     *
     * Only available when prayer times come from a mosque, since that is where the offsets come
     * from. Falls back to [FIXED_OFFSET] if no mosque is selected.
     */
    FROM_MOSQUE,

    /** Explicit wall-clock Iqama times. Deferred — see FUTURE_IMPROVEMENTS.md. */
    EXPLICIT_TIMES,

    /** Mosque-supplied schedule. Deferred — see FUTURE_IMPROVEMENTS.md. */
    MOSQUE_PROFILE,
}

/**
 * Iqama configuration. Defaults are the values specified in SPEC.md §"Method 1 — Fixed offset".
 *
 * The offsets are held per prayer rather than as a single number because SPEC.md's own defaults are
 * not uniform (Maghrib is +8 while the rest are +20/+25), and because "Individual offset" is a
 * required setting.
 */
@Serializable
public data class IqamaSettings(
    val mode: IqamaMode = IqamaMode.FIXED_OFFSET,
    val fajrOffset: Int = DEFAULT_FAJR_OFFSET,
    val dhuhrOffset: Int = DEFAULT_DHUHR_OFFSET,
    val asrOffset: Int = DEFAULT_ASR_OFFSET,
    val maghribOffset: Int = DEFAULT_MAGHRIB_OFFSET,
    val ishaOffset: Int = DEFAULT_ISHA_OFFSET,
) {
    /** Offset in minutes for [prayer], or null if it has no congregation ([Prayer.SUNRISE]). */
    public fun offsetFor(prayer: Prayer): Int? = when (prayer) {
        Prayer.FAJR -> fajrOffset
        Prayer.DHUHR -> dhuhrOffset
        Prayer.ASR -> asrOffset
        Prayer.MAGHRIB -> maghribOffset
        Prayer.ISHA -> ishaOffset
        Prayer.SUNRISE -> null
    }

    public companion object {
        public const val DEFAULT_FAJR_OFFSET: Int = 25
        public const val DEFAULT_DHUHR_OFFSET: Int = 20
        public const val DEFAULT_ASR_OFFSET: Int = 20
        public const val DEFAULT_MAGHRIB_OFFSET: Int = 8
        public const val DEFAULT_ISHA_OFFSET: Int = 20
    }
}
