package com.salahwatch.core.calculation

import com.salahwatch.core.model.DaySchedule
import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.ScheduleSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A single prayer, flattened out of its [DaySchedule] so that queries can run across day boundaries
 * without special-casing midnight.
 */
public data class PrayerOccurrence(
    val prayer: Prayer,
    val adhan: Instant,
    val iqama: Instant?,
    val date: LocalDate,
    val zone: ZoneId,
    val source: ScheduleSource,
) {
    /** The last instant this occurrence is "in effect" for countdown purposes. */
    public val lastMoment: Instant get() = iqama ?: adhan
}

/**
 * A continuous, ordered view over one or more days of prayer times.
 *
 * The whole point of this type is that **midnight is not a special case**. Callers ask "what is next
 * after this instant" and get an answer that may belong to tomorrow, without any date arithmetic at
 * the call site. That is what makes the Isha→Fajr rollover, a post-midnight Isha Iqama, and travel
 * across a date line behave correctly by construction rather than by patch.
 *
 * Construct via [of] so that overlapping or unsorted input is normalised once, up front.
 */
public class PrayerCalendar private constructor(
    public val occurrences: List<PrayerOccurrence>,
) {
    /** Every occurrence excluding [Prayer.SUNRISE], which is displayed but is not a prayer. */
    private val prayersOnly: List<PrayerOccurrence> = occurrences.filter { it.prayer.isPrayer }

    /** True when this calendar holds nothing usable. */
    public val isEmpty: Boolean get() = occurrences.isEmpty()

    /** The instant from which this calendar has data, or null if empty. */
    public val coverageStart: Instant? get() = occurrences.firstOrNull()?.adhan

    /** The last instant this calendar speaks for, or null if empty. */
    public val coverageEnd: Instant? get() = occurrences.lastOrNull()?.lastMoment

    /**
     * The next prayer whose Adhan is strictly after [at].
     *
     * Returns null when [at] is past the end of the loaded data — the caller must treat that as
     * "cache exhausted, refresh needed", never as "no more prayers".
     */
    public fun nextPrayer(at: Instant): PrayerOccurrence? =
        prayersOnly.firstOrNull { it.adhan > at }

    /**
     * The prayer currently in effect: the most recent one whose Adhan has already passed.
     *
     * Returns null before the first Adhan in the loaded window. Note this is *not* the complement of
     * [nextPrayer] at the boundary — exactly at an Adhan instant the prayer is current, not next.
     */
    public fun currentPrayer(at: Instant): PrayerOccurrence? =
        prayersOnly.lastOrNull { it.adhan <= at }

    /** The next occurrence of [prayer] specifically, at or after [at]. Used by the per-prayer complications. */
    public fun nextOccurrenceOf(prayer: Prayer, at: Instant): PrayerOccurrence? =
        occurrences.firstOrNull { it.prayer == prayer && it.adhan > at }

    /**
     * The next Iqama strictly after [at].
     *
     * This is deliberately separate from [nextPrayer]: between an Adhan and its Iqama, the next
     * *prayer* is the following one while the next *Iqama* still belongs to the current prayer.
     */
    public fun nextIqama(at: Instant): PrayerOccurrence? =
        prayersOnly.firstOrNull { it.iqama != null && it.iqama > at }

    /** Whether this calendar can answer questions about [at] without needing a refresh. */
    public fun covers(at: Instant): Boolean {
        val start = coverageStart
        val end = coverageEnd
        return start != null && end != null && at >= start && at <= end
    }

    public companion object {
        /**
         * Builds a calendar from any number of day schedules.
         *
         * Input may be unsorted and may overlap: schedules are merged so that a later-supplied day
         * wins for a given (date, prayer), which is what makes "refresh today from the network on
         * top of a cached estimate" a straight append rather than a cache-invalidation dance.
         */
        public fun of(schedules: Iterable<DaySchedule>): PrayerCalendar {
            val merged = LinkedHashMap<Pair<LocalDate, Prayer>, PrayerOccurrence>()
            for (day in schedules) {
                for (time in day.times) {
                    merged[day.date to time.prayer] = PrayerOccurrence(
                        prayer = time.prayer,
                        adhan = time.adhan,
                        iqama = time.iqama,
                        date = day.date,
                        zone = day.zone,
                        source = day.source,
                    )
                }
            }
            return PrayerCalendar(merged.values.sortedBy { it.adhan })
        }

        /** An empty calendar, for the "nothing cached yet" state. */
        public fun empty(): PrayerCalendar = PrayerCalendar(emptyList())
    }
}
