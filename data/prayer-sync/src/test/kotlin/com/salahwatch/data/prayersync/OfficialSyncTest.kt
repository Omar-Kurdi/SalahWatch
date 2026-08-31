package com.salahwatch.data.prayersync

import com.salahwatch.core.model.CalculationSettings
import com.salahwatch.core.model.DaySchedule
import com.salahwatch.core.model.LocationMode
import com.salahwatch.core.model.OfficialArea
import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.PrayerLocation
import com.salahwatch.core.model.PrayerTime
import com.salahwatch.core.model.ScheduleSource
import com.salahwatch.core.preferences.UserPreferences
import com.salahwatch.data.prayertimes.ScheduleCache
import com.salahwatch.data.prayertimes.ScheduleCacheKey
import com.salahwatch.core.model.TimeSource
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * How the syncer treats a bundled timetable that does not reach as far as it needs to.
 *
 * A bundle ends on the last day its authority had published, so running past the end of one is
 * normal operation rather than an error — it happens to every user eventually, and it happens
 * quietly. These tests pin the two things that must hold when it does: the published days are never
 * discarded in favour of calculated ones, and calculation is not fetched at all while the bundle
 * still covers the horizon, because needing no network is the whole point of this source.
 */
class OfficialSyncTest {

    private val alAin = PrayerLocation(24.2075, 55.7447, dubai)
    private val london = PrayerLocation(51.5074, -0.1278, ZoneId.of("Europe/London"))
    private val today = LocalDate.of(2026, 8, 14)

    private class FakeClient : PrayerTimeSource {
        var calls = 0
        override suspend fun annualCalendar(
            location: PrayerLocation,
            year: Int,
            settings: CalculationSettings,
        ): List<DaySchedule> {
            calls++
            return (1..LocalDate.of(year, 12, 31).dayOfYear).map {
                day(LocalDate.ofYearDay(year, it), ScheduleSource.ALADHAN)
            }
        }
    }

    private class FakeOfficial(private val days: List<DaySchedule>) : OfficialTimeSource {
        var askedFor: OfficialArea? = null
        override suspend fun calendarFor(area: OfficialArea): List<DaySchedule> {
            askedFor = area
            return days
        }
    }

    private class FakeCache : ScheduleCache {
        val stored = mutableMapOf<LocalDate, DaySchedule>()
        override suspend fun store(schedules: List<DaySchedule>) {
            schedules.forEach { stored[it.date] = it }
        }
        override suspend fun clear() = stored.clear()
        override suspend fun prune(before: LocalDate) {
            stored.keys.removeAll { it < before }
        }
        override suspend fun cachedDays(from: LocalDate, days: Int): Int =
            (0 until days).count { stored.containsKey(from.plusDays(it.toLong())) }
    }

    private class FakeState : SyncState {
        private var stored: String? = null
        override suspend fun matches(key: ScheduleCacheKey) = stored == key.toString()
        override suspend fun setKey(key: ScheduleCacheKey) {
            stored = key.toString()
        }
    }

    private fun run(
        official: FakeOfficial?,
        client: FakeClient,
        cache: FakeCache,
        location: PrayerLocation,
        area: OfficialArea? = null,
    ) = PrayerSyncer(
        source = client,
        officialSource = official,
        cache = cache,
        syncState = FakeState(),
        preferences = {
            UserPreferences(
                location = LocationMode.Manual(location),
                timeSource = TimeSource.OFFICIAL,
                officialArea = area,
            )
        },
        clock = Clock.fixed(today.atStartOfDay(dubai).toInstant(), dubai),
    )

    @Test
    fun `uses the bundle and touches no network when it covers the horizon`() = runTest {
        val bundle = datesFrom(today.minusDays(20), days = 200)
        val client = FakeClient()
        val cache = FakeCache()

        run(FakeOfficial(bundle), client, cache, alAin).sync()

        assertEquals(0, client.calls, "fetched calculated times despite a complete bundle")
        assertTrue(cache.stored.values.all { it.source == ScheduleSource.OFFICIAL })
    }

    @Test
    fun `picks the area from the location when none is pinned`() = runTest {
        val official = FakeOfficial(datesFrom(today, days = 200))
        run(official, FakeClient(), FakeCache(), alAin).sync()

        assertEquals(OfficialArea.AL_AIN, official.askedFor)
    }

    @Test
    fun `a pinned area wins over the location`() = runTest {
        val official = FakeOfficial(datesFrom(today, days = 200))
        run(official, FakeClient(), FakeCache(), alAin, area = OfficialArea.DUBAI).sync()

        assertEquals(OfficialArea.DUBAI, official.askedFor)
    }

    @Test
    fun `fills past the end of the bundle without losing the published days`() = runTest {
        // Ends inside the 60-day horizon, which is what the last weeks of a bundle look like.
        val lastPublished = today.plusDays(10)
        val bundle = datesFrom(today.minusDays(5), days = 16)
        val cache = FakeCache()
        val client = FakeClient()

        run(FakeOfficial(bundle), client, cache, alAin).sync()

        assertEquals(1, client.calls, "should have fetched calculated times for the remainder")
        assertEquals(ScheduleSource.OFFICIAL, cache.stored.getValue(lastPublished).source)
        assertEquals(ScheduleSource.OFFICIAL, cache.stored.getValue(today).source)
        assertEquals(
            ScheduleSource.ALADHAN,
            cache.stored.getValue(lastPublished.plusDays(1)).source,
        )
    }

    @Test
    fun `calculates where no bundled area is close enough`() = runTest {
        val official = FakeOfficial(datesFrom(today, days = 200))
        val cache = FakeCache()
        val client = FakeClient()

        run(official, client, cache, london).sync()

        assertEquals(null, official.askedFor, "asked for a timetable that cannot apply here")
        assertEquals(1, client.calls)
        assertTrue(cache.stored.values.all { it.source == ScheduleSource.ALADHAN })
    }

    private fun datesFrom(start: LocalDate, days: Int): List<DaySchedule> =
        (0 until days).map { day(start.plusDays(it.toLong()), ScheduleSource.OFFICIAL) }

    private companion object {
        val dubai: ZoneId = ZoneId.of("Asia/Dubai")

        fun day(date: LocalDate, source: ScheduleSource): DaySchedule {
            val base = date.atStartOfDay(dubai).toInstant()
            fun at(hours: Long): Instant = base.plusSeconds(hours * 3600)
            return DaySchedule(
                date = date,
                zone = dubai,
                times = listOf(
                    PrayerTime(Prayer.FAJR, at(4), null),
                    PrayerTime(Prayer.SUNRISE, at(6), null),
                    PrayerTime(Prayer.DHUHR, at(12), null),
                    PrayerTime(Prayer.ASR, at(15), null),
                    PrayerTime(Prayer.MAGHRIB, at(18), null),
                    PrayerTime(Prayer.ISHA, at(20), null),
                ),
                source = source,
            )
        }
    }
}
