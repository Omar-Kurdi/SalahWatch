package com.salahwatch.core.calculation

import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.PrayerTimes as AdhanPrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import com.salahwatch.core.model.CalculationSettings
import com.salahwatch.core.model.DaySchedule
import com.salahwatch.core.model.IqamaSettings
import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.PrayerLocation
import com.salahwatch.core.model.PrayerTime
import com.salahwatch.core.model.ScheduleSource
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Instant as KotlinInstant

/**
 * Computes prayer times on-device, with no network.
 *
 * This is the **fallback**, not the primary source. AlAdhan is authoritative whenever cached data
 * exists; this exists so that a fresh install with no connection, or travel to a new location while
 * offline, still shows a usable schedule. Everything it produces is tagged
 * [ScheduleSource.LOCAL_ESTIMATE] so the UI can mark it, per the project decision in RESEARCH.md §5.
 *
 * Backed by adhan2, which implements the astronomical equations from Jean Meeus's
 * *Astronomical Algorithms*. `AlAdhanMethodsGoldenTest` measures the residual disagreement with
 * AlAdhan against captured API responses, so the size of the "estimate" caveat is a known number
 * rather than a hope.
 */
public class LocalPrayerCalculator {

    /**
     * Calculates one day's schedule for [date] as it is reckoned in [location]'s own timezone.
     *
     * [date] is a local date deliberately: "today's prayers" is a wall-clock concept anchored to
     * where the user is, so the day boundary must come from [PrayerLocation.zone] rather than from
     * the device's current timezone. That is what keeps travel and manual clock changes correct.
     */
    public fun calculate(
        location: PrayerLocation,
        date: LocalDate,
        calculation: CalculationSettings,
        iqama: IqamaSettings,
    ): DaySchedule {
        val times = AdhanPrayerTimes(
            coordinates = Coordinates(location.latitude, location.longitude),
            dateComponents = DateComponents(date.year, date.monthValue, date.dayOfMonth),
            calculationParameters = AlAdhanMethods.parametersFor(calculation),
        )

        val schedule = DaySchedule(
            date = date,
            zone = location.zone,
            times = listOf(
                PrayerTime(Prayer.FAJR, times.fajr.toJava(), null),
                PrayerTime(Prayer.SUNRISE, times.sunrise.toJava(), null),
                PrayerTime(Prayer.DHUHR, times.dhuhr.toJava(), null),
                PrayerTime(Prayer.ASR, times.asr.toJava(), null),
                PrayerTime(Prayer.MAGHRIB, times.maghrib.toJava(), null),
                PrayerTime(Prayer.ISHA, times.isha.toJava(), null),
            ),
            source = ScheduleSource.LOCAL_ESTIMATE,
        )

        return IqamaCalculator.apply(schedule, iqama)
    }

    /**
     * Calculates [days] consecutive days starting at [from].
     *
     * Used to seed the cache offline. Each day is computed independently rather than by adding 24
     * hours, so DST transitions produce the correct local times instead of drifting by an hour.
     */
    public fun calculateRange(
        location: PrayerLocation,
        from: LocalDate,
        days: Int,
        calculation: CalculationSettings,
        iqama: IqamaSettings,
    ): List<DaySchedule> {
        require(days > 0) { "days must be positive" }
        return (0 until days).map { offset ->
            calculate(location, from.plusDays(offset.toLong()), calculation, iqama)
        }
    }
}

/** adhan2 returns `kotlin.time.Instant`; the rest of the app speaks `java.time`. */
private fun KotlinInstant.toJava(): Instant = Instant.ofEpochMilli(toEpochMilliseconds())
