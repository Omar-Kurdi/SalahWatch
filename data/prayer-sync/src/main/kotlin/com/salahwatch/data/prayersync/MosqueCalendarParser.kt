package com.salahwatch.data.prayersync

import com.salahwatch.core.model.DaySchedule
import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.PrayerTime
import com.salahwatch.core.model.ScheduleSource
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Why a mosque's published calendar could not be trusted. */
public sealed interface MosqueCalendarProblem {
    public data class TooFewTimes(val date: LocalDate, val count: Int) : MosqueCalendarProblem
    public data class Unparseable(val date: LocalDate, val value: String) : MosqueCalendarProblem

    /**
     * The times do not line up with where the sun actually is.
     *
     * Usually a mosque that has entered a timezone it is not in — one Istanbul mosque declares
     * `Asia/Jerusalem` — which would shift every prayer by hours.
     */
    public data class SunriseMismatch(val date: LocalDate, val offBy: Duration) : MosqueCalendarProblem
}

/** The outcome of reading a mosque's calendar. */
public sealed interface MosqueCalendarResult {
    public data class Usable(val schedules: List<DaySchedule>) : MosqueCalendarResult
    public data class Rejected(val problem: MosqueCalendarProblem) : MosqueCalendarResult
}

/**
 * Turns a mosque's published times into schedules, refusing anything that looks wrong.
 *
 * Mosque data is entered by people, so it has to be checked rather than trusted. Two hazards are
 * handled here, both found in real data while building this:
 *
 * **Variable length.** Most mosques publish six times, but many publish seven, prepending an extra
 * entry. Reading the array from the front would silently shift every prayer by one — Fajr showing
 * sunrise, and so on. Verified across mosques in three countries: **the last six entries are always
 * `[fajr, sunrise, dhuhr, asr, maghrib, isha]`**, so the array is read from the end.
 *
 * **Wrong timezone.** Sunrise is pure astronomy: it depends only on where and when, never on which
 * method or convention a mosque follows. That makes it the one value that can be checked
 * independently, so the parser compares the mosque's sunrise against a computed one and rejects the
 * calendar outright if they disagree by more than [SUNRISE_TOLERANCE].
 */
public class MosqueCalendarParser(
    private val expectedSunrise: (LocalDate) -> Instant?,
) {
    public fun parse(
        days: Map<LocalDate, List<String>>,
        zone: ZoneId,
    ): MosqueCalendarResult {
        val schedules = mutableListOf<DaySchedule>()

        for ((date, raw) in days.entries.sortedBy { it.key }) {
            when (val day = parseDay(date, raw, zone)) {
                is MosqueCalendarResult.Rejected -> return day
                is MosqueCalendarResult.Usable -> schedules += day.schedules
            }
        }

        return MosqueCalendarResult.Usable(schedules)
    }

    /** One day, either read or rejected with the reason. */
    private fun parseDay(
        date: LocalDate,
        raw: List<String>,
        zone: ZoneId,
    ): MosqueCalendarResult {
        val parsed = raw.takeLast(PRAYER_COUNT).map(::parseTime)
        val instants = parsed.filterNotNull()
            .map { date.atTime(it).atZone(zone).toInstant() }

        // Every reason to refuse is worked out first, so there is one exit and no chance of a
        // half-validated day slipping through.
        val problem = when {
            raw.size < PRAYER_COUNT ->
                MosqueCalendarProblem.TooFewTimes(date, raw.size)

            parsed.any { it == null } ->
                MosqueCalendarProblem.Unparseable(date, raw[parsed.indexOfFirst { it == null }])

            else -> sunriseProblem(date, instants[SUNRISE_INDEX])
        }

        return problem?.let { MosqueCalendarResult.Rejected(it) }
            ?: MosqueCalendarResult.Usable(
                listOf(
                    DaySchedule(
                        date = date,
                        zone = zone,
                        times = Prayer.entries.mapIndexed { index, prayer ->
                            // Iqama is applied later from settings, so it is null here.
                            PrayerTime(prayer, instants[index], null)
                        },
                        source = ScheduleSource.ALADHAN,
                    ),
                ),
            )
    }

    /** Null when the mosque's sunrise agrees with astronomy, or when there is nothing to compare. */
    private fun sunriseProblem(date: LocalDate, sunrise: Instant): MosqueCalendarProblem? {
        val expected = expectedSunrise(date) ?: return null
        val off = Duration.between(expected, sunrise).abs()
        return MosqueCalendarProblem.SunriseMismatch(date, off).takeIf { off > SUNRISE_TOLERANCE }
    }

    /** Accepts "HH:mm", tolerating stray whitespace and a trailing zone note. */
    private fun parseTime(value: String): LocalTime? {
        val trimmed = value.trim().take(TIME_LENGTH)
        return runCatching { LocalTime.parse(trimmed) }.getOrNull()
    }

    public companion object {
        private const val PRAYER_COUNT = 6
        private const val TIME_LENGTH = 5

        /** Sunrise sits second in the six-time layout. */
        private const val SUNRISE_INDEX = 1

        /**
         * How far a mosque's sunrise may sit from the astronomical one.
         *
         * Wide enough to allow the genuine variation seen in real data — mosques differ by up to
         * about half an hour depending on the convention and horizon they use — and far narrower
         * than the hours a timezone error produces.
         */
        public val SUNRISE_TOLERANCE: Duration = Duration.ofMinutes(45)
    }
}
