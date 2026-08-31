package com.salahwatch.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.Serializable

/**
 * Where a schedule's numbers came from.
 *
 * This is surfaced in the UI, not merely logged: per the project decision recorded in RESEARCH.md
 * §5, locally calculated times may differ from the authoritative AlAdhan values by a few minutes and
 * must be visibly marked wherever they are shown.
 */
@Serializable
public enum class ScheduleSource {
    /** Fetched from the AlAdhan API. Authoritative. */
    ALADHAN,

    /** Computed on-device by the offline fallback. Must be displayed as an estimate. */
    LOCAL_ESTIMATE,

    /**
     * Transcribed from an authority's own published timetable. Authoritative.
     *
     * Appended rather than inserted: `ScheduleCodec` writes this enum's *ordinal* over the Data
     * Layer, so reordering would silently reinterpret every schedule already on a watch.
     */
    OFFICIAL,
    ;

    public val isEstimate: Boolean get() = this == LOCAL_ESTIMATE
}

/**
 * One prayer on one day: when the Adhan is called, and when the congregation begins.
 *
 * [iqama] is null exactly when [prayer] has no congregation — i.e. for [Prayer.SUNRISE].
 */
@Serializable
public data class PrayerTime(
    val prayer: Prayer,
    @Serializable(with = InstantSerializer::class) val adhan: Instant,
    @Serializable(with = InstantSerializer::class) val iqama: Instant?,
) {
    init {
        require(prayer.isPrayer || iqama == null) { "${prayer.shortName} cannot have an Iqama time" }
        require(iqama == null || !iqama.isBefore(adhan)) {
            "Iqama for ${prayer.shortName} must not precede its Adhan"
        }
    }
}

/**
 * A complete day of prayer times, resolved to absolute instants.
 *
 * Times are stored as [Instant] rather than wall-clock strings deliberately. The API returns
 * offset-aware timestamps when queried with `iso8601=true`, and keeping them absolute is what makes
 * DST transitions, timezone travel and midnight crossings fall out correctly instead of needing
 * special cases — a UTC instant means the same moment regardless of what the device clock is doing.
 *
 * [zone] is retained separately because *rendering* still needs it: "4:32 PM" is a local-time
 * concept, and the day boundary used for grouping is a local-date concept.
 */
@Serializable
public data class DaySchedule(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    @Serializable(with = ZoneIdSerializer::class) val zone: ZoneId,
    val times: List<PrayerTime>,
    val source: ScheduleSource,
) {
    init {
        require(times.isNotEmpty()) { "A day schedule cannot be empty" }
        require(times.map { it.prayer }.distinct().size == times.size) {
            "Duplicate prayer in schedule for $date"
        }
        require(times.zipWithNext().all { (a, b) -> !b.adhan.isBefore(a.adhan) }) {
            "Prayer times for $date are not in chronological order"
        }
    }

    /** Lookup by prayer, or null if this day's source did not report it. */
    public operator fun get(prayer: Prayer): PrayerTime? = times.firstOrNull { it.prayer == prayer }

    /** The five obligatory prayers only, in order — [Prayer.SUNRISE] removed. */
    public val obligatory: List<PrayerTime> get() = times.filter { it.prayer.isPrayer }

    /** The first Adhan of the day; used to decide whether an instant precedes this day's schedule. */
    public val firstAdhan: Instant get() = times.first().adhan

    /** The last moment this schedule speaks for — the final Iqama if there is one, else the final Adhan. */
    public val lastMoment: Instant
        get() = times.last().let { it.iqama ?: it.adhan }
}
