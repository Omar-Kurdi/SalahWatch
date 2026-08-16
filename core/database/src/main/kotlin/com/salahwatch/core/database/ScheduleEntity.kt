package com.salahwatch.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.salahwatch.core.model.DaySchedule
import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.PrayerTime
import com.salahwatch.core.model.ScheduleSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One cached day of Adhan times.
 *
 * The six times are stored as separate epoch-millisecond columns rather than as a serialised list.
 * That keeps the table queryable — "give me the days covering this window" is a range scan — and
 * avoids a converter on the hot path that the complication service hits.
 *
 * **Iqama times are not stored.** They are derived from the Adhan times plus the user's offsets, so
 * persisting them would mean every Iqama-offset change required rewriting the whole cache, and would
 * create a second copy of the truth that could go stale.
 *
 * The cache holds one location's schedule at a time; [ScheduleCacheKey] records which, and the cache
 * is cleared wholesale when that changes.
 */
@Entity(tableName = "day_schedule")
public data class ScheduleEntity(
    @PrimaryKey val epochDay: Long,
    val zoneId: String,
    val source: String,
    val fajr: Long,
    val sunrise: Long,
    val dhuhr: Long,
    val asr: Long,
    val maghrib: Long,
    val isha: Long,
) {
    /** Converts back to the domain type. Iqama times are left null for the caller to derive. */
    public fun toDaySchedule(): DaySchedule = DaySchedule(
        date = LocalDate.ofEpochDay(epochDay),
        zone = ZoneId.of(zoneId),
        times = listOf(
            PrayerTime(Prayer.FAJR, Instant.ofEpochMilli(fajr), null),
            PrayerTime(Prayer.SUNRISE, Instant.ofEpochMilli(sunrise), null),
            PrayerTime(Prayer.DHUHR, Instant.ofEpochMilli(dhuhr), null),
            PrayerTime(Prayer.ASR, Instant.ofEpochMilli(asr), null),
            PrayerTime(Prayer.MAGHRIB, Instant.ofEpochMilli(maghrib), null),
            PrayerTime(Prayer.ISHA, Instant.ofEpochMilli(isha), null),
        ),
        source = ScheduleSource.valueOf(source),
    )

    public companion object {
        /** Converts a domain schedule for storage. Throws if a prayer is missing. */
        public fun from(schedule: DaySchedule): ScheduleEntity {
            fun adhanOf(prayer: Prayer): Long = requireNotNull(schedule[prayer]) {
                "Schedule for ${schedule.date} is missing ${prayer.shortName}"
            }.adhan.toEpochMilli()

            return ScheduleEntity(
                epochDay = schedule.date.toEpochDay(),
                zoneId = schedule.zone.id,
                source = schedule.source.name,
                fajr = adhanOf(Prayer.FAJR),
                sunrise = adhanOf(Prayer.SUNRISE),
                dhuhr = adhanOf(Prayer.DHUHR),
                asr = adhanOf(Prayer.ASR),
                maghrib = adhanOf(Prayer.MAGHRIB),
                isha = adhanOf(Prayer.ISHA),
            )
        }
    }
}
