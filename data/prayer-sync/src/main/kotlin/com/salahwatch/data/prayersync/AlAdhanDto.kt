package com.salahwatch.data.prayersync

import com.salahwatch.core.model.DaySchedule
import com.salahwatch.data.prayertimes.PrayerScheduleRepository
import com.salahwatch.data.prayertimes.ScheduleCache
import com.salahwatch.data.prayertimes.ScheduleCacheKey
import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.PrayerTime
import com.salahwatch.core.model.ScheduleSource
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The subset of the AlAdhan calendar response this app reads.
 *
 * Requests always set `iso8601=true`, so every timing arrives as a full offset-aware timestamp such
 * as `2026-10-25T04:49:00+00:00` rather than a bare `04:49`. That single parameter is what makes
 * daylight-saving transitions correct for free: the offset in the string is the authoritative one
 * for that date, so no local reinterpretation is needed and none is done.
 *
 * Unknown fields are ignored by the decoder — the API returns Hijri dates, Imsak, Midnight and
 * thirds of the night that this app has no use for.
 */
@Serializable
internal data class CalendarResponse<T>(
    val code: Int,
    val status: String,
    val data: T,
)

/** Annual responses key their months "1".."12"; monthly responses are a bare list. */
internal typealias AnnualData = Map<String, List<CalendarDay>>

@Serializable
internal data class CalendarDay(
    val timings: Timings,
    val date: DateBlock,
    val meta: MetaBlock,
) {
    /**
     * Converts to the domain type.
     *
     * Iqama is left null: it is derived from user settings, not supplied by the API, and deriving it
     * here would bake a preference into cached data.
     */
    fun toDaySchedule(): DaySchedule = DaySchedule(
        date = LocalDate.parse(date.gregorian.date, GREGORIAN_FORMAT),
        zone = ZoneId.of(meta.timezone),
        times = listOf(
            PrayerTime(Prayer.FAJR, timings.fajr.toInstant(), null),
            PrayerTime(Prayer.SUNRISE, timings.sunrise.toInstant(), null),
            PrayerTime(Prayer.DHUHR, timings.dhuhr.toInstant(), null),
            PrayerTime(Prayer.ASR, timings.asr.toInstant(), null),
            PrayerTime(Prayer.MAGHRIB, timings.maghrib.toInstant(), null),
            PrayerTime(Prayer.ISHA, timings.isha.toInstant(), null),
        ),
        source = ScheduleSource.ALADHAN,
    )

    private fun String.toInstant(): java.time.Instant = OffsetDateTime.parse(this).toInstant()
}

// File-level rather than a companion object: kotlinx.serialization generates its own `Companion` on
// @Serializable classes, and declaring a private one alongside it fails at runtime with
// IllegalAccessError.
private val GREGORIAN_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

@Serializable
internal data class Timings(
    @SerialName("Fajr") val fajr: String,
    @SerialName("Sunrise") val sunrise: String,
    @SerialName("Dhuhr") val dhuhr: String,
    @SerialName("Asr") val asr: String,
    @SerialName("Maghrib") val maghrib: String,
    @SerialName("Isha") val isha: String,
)

@Serializable
internal data class DateBlock(val gregorian: GregorianBlock)

@Serializable
internal data class GregorianBlock(val date: String)

@Serializable
internal data class MetaBlock(val timezone: String)
