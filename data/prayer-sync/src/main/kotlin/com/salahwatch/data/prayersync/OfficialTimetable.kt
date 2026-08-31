package com.salahwatch.data.prayersync

import com.salahwatch.core.model.DaySchedule
import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.PrayerTime
import com.salahwatch.core.model.ScheduleSource
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A bundled official timetable, as it is stored on disk.
 *
 * The times are one flat string per month rather than nested objects because the whole point of the
 * file is to be small and diffable: a year is 365 fixed-width records, so a month is one line, and a
 * yearly refresh shows up in review as the lines that actually changed rather than as a rewritten
 * document.
 */
@Serializable
internal data class OfficialTimetableFile(
    val area: String,
    val slug: String,
    val zone: String,
    val source: String,
    val retrieved: String,
    val order: List<String>,
    /** Keyed `YYYY-M`; each value is comma-separated days, each day six `HHMM` times. */
    val months: Map<String, String>,
)

/**
 * Turns a bundled timetable into schedules, rejecting anything that does not look like one.
 *
 * The validation matters more here than it might appear. These files are produced by transcribing a
 * published table, and a transcription error is silent by nature: every value is a plausible time,
 * so nothing downstream can notice that Asr and Maghrib were swapped or a day was dropped. Checking
 * the shape at load is the only place the mistake is still cheap to catch, so a malformed file is
 * refused wholesale rather than partially trusted.
 */
internal object OfficialTimetableParser {

    /** The prayers a bundled day carries, in the order they appear in the file. */
    private val ORDER = listOf(
        Prayer.FAJR,
        Prayer.SUNRISE,
        Prayer.DHUHR,
        Prayer.ASR,
        Prayer.MAGHRIB,
        Prayer.ISHA,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parsed schedules in date order, or null when the file is unusable.
     *
     * Written as a series of early returns because every one of them is the same decision — this
     * file is not trustworthy, so use none of it. Collapsing them into one exit would replace that
     * with bookkeeping about which check failed, which nothing here needs.
     */
    @Suppress("ReturnCount")
    fun parse(text: String): List<DaySchedule>? {
        val file = runCatching { json.decodeFromString<OfficialTimetableFile>(text) }.getOrNull()
            ?: return null
        if (file.order.size != ORDER.size) return null

        val zone = runCatching { ZoneId.of(file.zone) }.getOrNull() ?: return null

        val schedules = ArrayList<DaySchedule>(DAYS_PER_YEAR)
        for ((key, packed) in file.months) {
            val month = parseMonthKey(key) ?: return null
            packed.split(',').forEachIndexed { index, day ->
                val date = dateOrNull(month.first, month.second, index + 1) ?: return null
                schedules += scheduleOrNull(date, zone, day) ?: return null
            }
        }
        return schedules.takeIf { it.isNotEmpty() }?.sortedBy { it.date }
    }

    /** `YYYY-M` to a year/month pair, rejecting anything else. */
    @Suppress("ReturnCount")
    private fun parseMonthKey(key: String): Pair<Int, Int>? {
        val parts = key.split('-')
        if (parts.size != 2) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        return if (month in 1..MONTHS_PER_YEAR) year to month else null
    }

    private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    /**
     * One day's six times, or null when the record is the wrong length, holds an impossible time,
     * or is out of order — the three ways a transcription slip shows up in the data.
     */
    @Suppress("ReturnCount")
    private fun scheduleOrNull(date: LocalDate, zone: ZoneId, packed: String): DaySchedule? {
        if (packed.length != ORDER.size * WIDTH) return null

        val times = ArrayList<PrayerTime>(ORDER.size)
        for ((slot, prayer) in ORDER.withIndex()) {
            val field = packed.substring(slot * WIDTH, (slot + 1) * WIDTH)
            val hour = field.take(HOUR_WIDTH).toIntOrNull() ?: return null
            val minute = field.drop(HOUR_WIDTH).toIntOrNull() ?: return null
            val local = runCatching { LocalTime.of(hour, minute) }.getOrNull() ?: return null
            times += PrayerTime(
                prayer = prayer,
                adhan = date.atTime(local).atZone(zone).toInstant(),
                // A published table gives the Adhan only; Iqama comes from the user's own settings,
                // applied downstream by IqamaCalculator exactly as it is for every other source.
                iqama = null,
            )
        }
        if (times.zipWithNext().any { (a, b) -> !b.adhan.isAfter(a.adhan) }) return null

        return DaySchedule(date = date, zone = zone, times = times, source = ScheduleSource.OFFICIAL)
    }

    /** `HHMM`: each field is a fixed-width hour followed by a fixed-width minute. */
    private const val HOUR_WIDTH = 2
    private const val WIDTH = HOUR_WIDTH * 2
    private const val MONTHS_PER_YEAR = 12
    private const val DAYS_PER_YEAR = 366
}
