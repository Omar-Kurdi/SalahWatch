package com.salahwatch.data.prayersync

import com.salahwatch.core.model.CalculationSettings
import com.salahwatch.core.model.DaySchedule
import com.salahwatch.data.prayertimes.PrayerScheduleRepository
import com.salahwatch.data.prayertimes.ScheduleCache
import com.salahwatch.data.prayertimes.ScheduleCacheKey
import com.salahwatch.core.model.LocationMode
import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.PrayerLocation
import com.salahwatch.core.model.PrayerTime
import com.salahwatch.core.model.ScheduleSource
import com.salahwatch.core.preferences.UserPreferences
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Covers the decisions the syncer makes, not the HTTP call.
 *
 * These are the failure modes that would be invisible in use: a cache silently kept after the user
 * moves, a refetch on every wake-up, or a year boundary that leaves the app with no data on 1
 * January. Each is cheap to test here and expensive to notice in the field.
 */
class PrayerSyncerTest {

    private val alAin = PrayerLocation(24.2075, 55.7447, ZoneId.of("Asia/Dubai"))
    private val london = PrayerLocation(51.5074, -0.1278, ZoneId.of("Europe/London"))
    private val today = LocalDate.of(2026, 8, 14)

    // --- Fakes -----------------------------------------------------------------------------

    private class FakeClient : PrayerTimeSource {
        var calls = 0
        var yearsRequested = mutableListOf<Int>()
        var failWith: Throwable? = null

        override suspend fun annualCalendar(
            location: PrayerLocation,
            year: Int,
            settings: CalculationSettings,
        ): List<DaySchedule> {
            failWith?.let { throw it }
            calls++
            yearsRequested += year
            return (0 until 365).map { day ->
                val date = LocalDate.ofYearDay(year, day + 1)
                schedule(date, location.zone)
            }
        }
    }

    private class FakeRepository : ScheduleCache {
        val stored = mutableMapOf<LocalDate, DaySchedule>()
        var clearCount = 0
        var prunedBefore: LocalDate? = null

        override suspend fun store(schedules: List<DaySchedule>) {
            schedules.forEach { stored[it.date] = it }
        }

        override suspend fun clear() {
            clearCount++
            stored.clear()
        }

        override suspend fun prune(before: LocalDate) {
            prunedBefore = before
            stored.keys.removeAll { it < before }
        }

        override suspend fun cachedDays(from: LocalDate, days: Int): Int =
            (0 until days).count { stored.containsKey(from.plusDays(it.toLong())) }
    }

    private class FakeSyncState : SyncState {
        private var stored: String? = null
        override suspend fun matches(key: ScheduleCacheKey) = stored == key.asString()
        override suspend fun setKey(key: ScheduleCacheKey) {
            stored = key.asString()
        }
    }

    private fun syncer(
        client: FakeClient,
        repository: FakeRepository,
        state: FakeSyncState,
        preferences: UserPreferences,
    ) = PrayerSyncer(
        source = client,
        cache = repository,
        syncState = state,
        preferences = { preferences },
        clock = Clock.fixed(
            today.atStartOfDay(ZoneId.of("Asia/Dubai")).toInstant(),
            ZoneId.of("Asia/Dubai"),
        ),
    )

    // --- Tests -----------------------------------------------------------------------------

    @Test
    fun `fetches when nothing is cached`() = runTest {
        val client = FakeClient()
        val repo = FakeRepository()
        val result = syncer(client, repo, FakeSyncState(), prefs(alAin)).sync()

        assertIs<SyncResult.Fetched>(result)
        assertEquals(1, client.calls)
        assertTrue(repo.stored.isNotEmpty())
    }

    @Test
    fun `does not refetch when the cache already covers the horizon`() = runTest {
        val client = FakeClient()
        val repo = FakeRepository()
        val state = FakeSyncState()
        val preferences = prefs(alAin)

        syncer(client, repo, state, preferences).sync()
        val second = syncer(client, repo, state, preferences).sync()

        assertIs<SyncResult.AlreadyCurrent>(second)
        assertEquals(1, client.calls, "A daily wake-up must not hit the network for nothing")
    }

    @Test
    fun `refetches and clears the cache when the location changes`() = runTest {
        val client = FakeClient()
        val repo = FakeRepository()
        val state = FakeSyncState()

        syncer(client, repo, state, prefs(alAin)).sync()
        // The first sync also clears, because there is no previous key to match against. What
        // matters is that moving triggers a *further* clear.
        val clearsBefore = repo.clearCount
        val moved = syncer(client, repo, state, prefs(london)).sync()

        assertIs<SyncResult.Fetched>(moved)
        assertEquals(2, client.calls)
        assertEquals(
            clearsBefore + 1, repo.clearCount,
            "Old city's times must not survive alongside the new ones",
        )
    }

    @Test
    fun `refetches when the calculation method changes`() = runTest {
        val client = FakeClient()
        val repo = FakeRepository()
        val state = FakeSyncState()

        syncer(client, repo, state, prefs(alAin)).sync()
        val clearsBefore = repo.clearCount
        val changed = syncer(
            client, repo, state,
            prefs(alAin).copy(calculation = CalculationSettings(methodId = 3)),
        ).sync()

        assertIs<SyncResult.Fetched>(changed)
        assertEquals(clearsBefore + 1, repo.clearCount, "Method 3 and 16 times must not be mixed")
    }

    @Test
    fun `ignores GPS jitter below the rounding threshold`() = runTest {
        val client = FakeClient()
        val repo = FakeRepository()
        val state = FakeSyncState()

        syncer(client, repo, state, prefs(alAin)).sync()
        // ~10 m of drift: a real fix wobbles this much sitting still.
        val jittered = alAin.copy(latitude = alAin.latitude + 0.0001)
        val second = syncer(client, repo, state, prefs(jittered)).sync()

        assertIs<SyncResult.AlreadyCurrent>(second)
        assertEquals(1, client.calls, "A wobbling GPS fix must not refetch a year of data")
    }

    @Test
    fun `force refetches even when the cache looks fine`() = runTest {
        val client = FakeClient()
        val repo = FakeRepository()
        val state = FakeSyncState()
        val preferences = prefs(alAin)

        syncer(client, repo, state, preferences).sync()
        val forced = syncer(client, repo, state, preferences).sync(force = true)

        assertIs<SyncResult.Fetched>(forced)
        assertEquals(2, client.calls)
    }

    @Test
    fun `reports no location rather than failing`() = runTest {
        val result = syncer(
            FakeClient(), FakeRepository(), FakeSyncState(),
            UserPreferences(location = LocationMode.Automatic(last = null)),
        ).sync()

        assertIs<SyncResult.NoLocation>(result)
    }

    @Test
    fun `network failure is retryable, not fatal`() = runTest {
        val client = FakeClient().apply { failWith = IOException("offline") }
        val result = syncer(client, FakeRepository(), FakeSyncState(), prefs(alAin)).sync()

        assertIs<SyncResult.Failed>(result)
    }

    @Test
    fun `fetches next year too once the horizon crosses the boundary`() = runTest {
        val client = FakeClient()
        val repo = FakeRepository()
        val december = LocalDate.of(2026, 12, 1)

        PrayerSyncer(
            source = client,
            cache = repo,
            syncState = FakeSyncState(),
            preferences = { prefs(alAin) },
            clock = Clock.fixed(
                december.atStartOfDay(ZoneId.of("Asia/Dubai")).toInstant(),
                ZoneId.of("Asia/Dubai"),
            ),
        ).sync()

        assertEquals(
            listOf(2026, 2027), client.yearsRequested,
            "The turn of the year must not be a cliff the app falls off",
        )
    }

    @Test
    fun `prunes stale days but keeps yesterday`() = runTest {
        val repo = FakeRepository()
        syncer(FakeClient(), repo, FakeSyncState(), prefs(alAin)).sync()

        assertEquals(today.minusDays(PrayerSyncer.RETAIN_PAST_DAYS), repo.prunedBefore)
        assertTrue(
            repo.stored.containsKey(today.minusDays(1)),
            "Yesterday is needed for an Isha Iqama that crosses midnight",
        )
    }

    private fun prefs(location: PrayerLocation) =
        UserPreferences(location = LocationMode.Manual(location))
}

private fun schedule(date: LocalDate, zone: ZoneId): DaySchedule {
    val base = date.atStartOfDay(zone).toInstant()
    fun at(hours: Long): Instant = base.plusSeconds(hours * 3600)
    return DaySchedule(
        date = date,
        zone = zone,
        times = listOf(
            PrayerTime(Prayer.FAJR, at(4), null),
            PrayerTime(Prayer.SUNRISE, at(6), null),
            PrayerTime(Prayer.DHUHR, at(12), null),
            PrayerTime(Prayer.ASR, at(15), null),
            PrayerTime(Prayer.MAGHRIB, at(18), null),
            PrayerTime(Prayer.ISHA, at(20), null),
        ),
        source = ScheduleSource.ALADHAN,
    )
}
