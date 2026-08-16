package com.salahwatch.core.calculation

import com.salahwatch.core.model.DaySchedule
import com.salahwatch.core.model.IqamaSettings
import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.PrayerTime
import com.salahwatch.core.model.ScheduleSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal val DUBAI: ZoneId = ZoneId.of("Asia/Dubai")
internal val LONDON: ZoneId = ZoneId.of("Europe/London")

/** Resolves a local wall-clock time on [date] in [zone] to an absolute instant. */
internal fun at(date: LocalDate, zone: ZoneId, time: String): Instant =
    date.atTime(LocalTime.parse(time)).atZone(zone).toInstant()

/**
 * Builds a day schedule from wall-clock strings, applying Iqama offsets.
 *
 * Times are given as local wall-clock so that the tests read like the API responses and the watch
 * face, while the code under test only ever sees absolute instants — which is exactly the boundary
 * where DST bugs would hide.
 */
internal fun daySchedule(
    date: LocalDate,
    zone: ZoneId = DUBAI,
    fajr: String = "04:30",
    sunrise: String = "05:52",
    dhuhr: String = "12:25",
    asr: String = "15:49",
    maghrib: String = "18:55",
    isha: String = "20:13",
    iqama: IqamaSettings? = IqamaSettings(),
    source: ScheduleSource = ScheduleSource.ALADHAN,
): DaySchedule {
    val schedule = DaySchedule(
        date = date,
        zone = zone,
        times = listOf(
            PrayerTime(Prayer.FAJR, at(date, zone, fajr), null),
            PrayerTime(Prayer.SUNRISE, at(date, zone, sunrise), null),
            PrayerTime(Prayer.DHUHR, at(date, zone, dhuhr), null),
            PrayerTime(Prayer.ASR, at(date, zone, asr), null),
            PrayerTime(Prayer.MAGHRIB, at(date, zone, maghrib), null),
            PrayerTime(Prayer.ISHA, at(date, zone, isha), null),
        ),
        source = source,
    )
    return iqama?.let { IqamaCalculator.apply(schedule, it) } ?: schedule
}
