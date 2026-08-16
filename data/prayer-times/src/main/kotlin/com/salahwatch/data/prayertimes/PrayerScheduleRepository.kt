package com.salahwatch.data.prayertimes

import com.salahwatch.core.calculation.IqamaCalculator
import com.salahwatch.core.calculation.LocalPrayerCalculator
import com.salahwatch.core.calculation.PrayerCalendar
import com.salahwatch.core.database.ScheduleDao
import com.salahwatch.core.database.ScheduleEntity
import com.salahwatch.core.model.DaySchedule
import com.salahwatch.core.model.PrayerLocation
import com.salahwatch.core.model.effectiveLocation
import com.salahwatch.core.model.shiftedBy
import com.salahwatch.core.preferences.PreferencesRepository
import com.salahwatch.core.preferences.UserPreferences
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The single place anything asks "what are the prayer times".
 *
 * Resolution order is cache first, then local calculation for anything the cache does not cover.
 * Notably it never touches the network: fetching is a separate, deliberate act performed by the
 * phone's sync worker. A complication service asking for today's times must never be able to
 * trigger an HTTP request — that is the difference between a complication that costs nothing and one
 * that drains the battery.
 *
 * Iqama times are derived here rather than stored, so changing an Iqama offset takes effect
 * immediately without rewriting the cache.
 */
public class PrayerScheduleRepository(
    private val dao: ScheduleDao,
    private val preferences: PreferencesRepository,
    private val calculator: LocalPrayerCalculator = LocalPrayerCalculator(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ScheduleCache {
    /**
     * A calendar covering [days] days starting at [from], in the configured location's own timezone.
     *
     * Returns an empty calendar when no location is known yet — a fresh install in Automatic mode
     * before the first fix. Callers must render that as "no data", never as a zeroed countdown.
     */
    public suspend fun calendar(from: LocalDate, days: Int): PrayerCalendar = withContext(io) {
        require(days > 0) { "days must be positive" }
        val prefs = preferences.current()
        val location = prefs.location.effectiveLocation ?: return@withContext PrayerCalendar.empty()

        val wanted = (0 until days).map { from.plusDays(it.toLong()) }
        val cached = dao.range(wanted.first().toEpochDay(), wanted.last().toEpochDay())
            .associateBy { LocalDate.ofEpochDay(it.epochDay) }

        val schedules = wanted.map { date ->
            val fromCache = cached[date]?.toDaySchedule()
            fromCache ?: calculator.calculate(location, date, prefs.calculation, prefs.iqama)
        }

        PrayerCalendar.of(schedules.map { it.presented(prefs) })
    }

    /**
     * A cached day turned into what the user should actually see.
     *
     * Corrections and Iqama are both applied here rather than stored, and in that order: a
     * correction moves the Adhan, and the congregation follows the Adhan it belongs to. Doing it on
     * read is what makes both take effect the moment they are changed, and — for corrections — what
     * makes them work against a mosque's published table, which no fetch parameter can shift.
     */
    private fun DaySchedule.presented(prefs: UserPreferences): DaySchedule = IqamaCalculator.apply(
        schedule = shiftedBy(prefs.calculation.adjustments),
        settings = prefs.iqama,
        mosqueOffsets = prefs.mosque?.iqamaOffsets,
        mosqueTimes = prefs.mosque?.iqamaTimes,
    )

    /**
     * Emits whenever the cached schedule changes.
     *
     * The UI needs this because a sync happens in a worker: without it a screen shows whatever was
     * in the cache when it last recomposed, so a fetch that lands a second later is invisible until
     * something else happens to trigger a reload. That produced an off-by-one where each settings
     * change displayed the *previous* change's result.
     *
     * Takes no date. An earlier version observed the timeline window, which meant a caller had to
     * pass `LocalDate.now()` — captured once, at construction, and therefore wrong for anything
     * left running past midnight.
     */
    public fun scheduleChanges(): Flow<Unit> = dao.observeChanges().map { }

    /**
     * The raw day schedules for a window, resolved the same way [calendar] resolves them.
     *
     * Exists for the Data Layer publisher, which sends days rather than a flattened calendar so the
     * watch can rebuild its own calendar and apply its own Iqama settings.
     */
    public suspend fun daySchedules(from: LocalDate, days: Int): List<DaySchedule> = withContext(io) {
        require(days > 0) { "days must be positive" }
        val prefs = preferences.current()
        val location = prefs.location.effectiveLocation ?: return@withContext emptyList()

        val wanted = (0 until days).map { from.plusDays(it.toLong()) }
        val cached = dao.range(wanted.first().toEpochDay(), wanted.last().toEpochDay())
            .associateBy { LocalDate.ofEpochDay(it.epochDay) }

        wanted.map { date ->
            cached[date]?.toDaySchedule()
                ?: calculator.calculate(location, date, prefs.calculation, prefs.iqama)
        }
    }

    /**
     * A calendar wide enough for a complication timeline.
     *
     * Starts a day early so that a prayer late on the previous day — an Isha whose Iqama falls after
     * midnight — is still present, and runs far enough ahead that a device which fails to refresh
     * for a day still renders correctly.
     */
    public suspend fun timelineCalendar(today: LocalDate): PrayerCalendar =
        calendar(today.minusDays(1), TIMELINE_DAYS)

    /** Stores authoritative schedules, replacing any local estimates for the same days. */
    override suspend fun store(schedules: List<DaySchedule>): Unit = withContext(io) {
        dao.upsert(schedules.map(ScheduleEntity::from))
    }

    /** Drops everything. Used when the location or calculation settings change. */
    override suspend fun clear(): Unit = withContext(io) { dao.clear() }

    /** Drops days before [before], keeping a year-long cache from growing without bound. */
    override suspend fun prune(before: LocalDate): Unit = withContext(io) {
        dao.pruneBefore(before.toEpochDay())
    }

    /** How many days of authoritative data are cached from [from] onwards. */
    override suspend fun cachedDays(from: LocalDate, days: Int): Int = withContext(io) {
        dao.range(from.toEpochDay(), from.plusDays(days.toLong() - 1).toEpochDay()).size
    }

    public companion object {
        /**
         * Yesterday, today and the next two days.
         *
         * Four days rather than two so a missed daily refresh degrades gracefully instead of
         * leaving the watch face blank — the timeline is built from whatever this returns.
         */
        public const val TIMELINE_DAYS: Int = 4
    }
}

/** The storage side of the cache, as the syncer needs it. */
public interface ScheduleCache {
    public suspend fun store(schedules: List<DaySchedule>)
    public suspend fun clear()
    public suspend fun prune(before: LocalDate)
    public suspend fun cachedDays(from: LocalDate, days: Int): Int
}

/**
 * Identifies what a cached schedule was fetched for, so a stale cache can be detected.
 *
 * Corrections are deliberately **not** part of this. They are applied on read rather than baked
 * into what is cached, so changing one cannot invalidate a year of data — which is what used to
 * make every tap on a correction stepper spend a network round trip before it showed anything.
 *
 * The chosen source *is* part of it, because switching between calculation and a mosque — or
 * between two mosques — replaces the times themselves. Without it a mosque whose published year is
 * shorter than the previous fetch would leave the uncovered days holding the old source's answers.
 */
public data class ScheduleCacheKey(
    val latitude: Double,
    val longitude: Double,
    val zoneId: String,
    val methodId: Int,
    val school: String,
    /** The time source, and which mosque, when one is chosen. */
    val source: String,
) {
    public companion object {
        public fun of(preferences: UserPreferences, location: PrayerLocation): ScheduleCacheKey =
            ScheduleCacheKey(
                latitude = location.latitude,
                longitude = location.longitude,
                zoneId = location.zone.id,
                methodId = preferences.calculation.methodId,
                school = preferences.calculation.school.name,
                source = listOfNotNull(
                    preferences.timeSource.name,
                    preferences.mosque?.id,
                ).joinToString(":"),
            )
    }
}
