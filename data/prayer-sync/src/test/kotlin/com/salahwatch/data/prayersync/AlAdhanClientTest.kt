package com.salahwatch.data.prayersync

import com.salahwatch.core.model.CalculationSettings
import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.PrayerAdjustments
import com.salahwatch.core.model.PrayerLocation
import com.salahwatch.core.model.School
import com.salahwatch.core.model.ScheduleSource
import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Exercises the client against a recorded AlAdhan response rather than the live API.
 *
 * The fixture is a real two-month slice captured on 2026-08-14, trimmed to the fields the client
 * reads. Recording it means the parsing contract is pinned and the tests stay runnable offline.
 */
class AlAdhanClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: AlAdhanClient

    private val alAin = PrayerLocation(24.2075, 55.7447, ZoneId.of("Asia/Dubai"))

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = AlAdhanClient(baseUrl = server.url("/v1/").toString().toHttpUrl())
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    private fun enqueueFixture() {
        val body = checkNotNull(javaClass.getResourceAsStream("/aladhan-calendar-annual.json")) {
            "fixture missing"
        }.reader().readText()
        server.enqueue(MockResponse(code = 200, body = body))
    }

    @Test
    fun `parses a calendar into day schedules`() = runTest {
        enqueueFixture()

        val schedules = client.annualCalendar(alAin, 2026, CalculationSettings())

        assertEquals(61, schedules.size, "Fixture holds August and September")
        assertTrue(schedules.zipWithNext().all { (a, b) -> a.date < b.date }, "Must be date-ordered")
        assertTrue(schedules.all { it.source == ScheduleSource.ALADHAN })
    }

    @Test
    fun `preserves the exact times the API returned`() = runTest {
        enqueueFixture()

        val schedules = client.annualCalendar(alAin, 2026, CalculationSettings())
        val aug14 = schedules.single { it.date == LocalDate.of(2026, 8, 14) }

        // Recorded from the live API for these coordinates under the default method.
        fun adhanAt(prayer: Prayer) = aug14[prayer]?.adhan
        assertEquals(OffsetDateTime.parse("2026-08-14T04:30:00+04:00").toInstant(), adhanAt(Prayer.FAJR))
        assertEquals(OffsetDateTime.parse("2026-08-14T12:25:00+04:00").toInstant(), adhanAt(Prayer.DHUHR))
        assertEquals(OffsetDateTime.parse("2026-08-14T15:49:00+04:00").toInstant(), adhanAt(Prayer.ASR))
        assertEquals(OffsetDateTime.parse("2026-08-14T18:55:00+04:00").toInstant(), adhanAt(Prayer.MAGHRIB))
        assertEquals(OffsetDateTime.parse("2026-08-14T20:13:00+04:00").toInstant(), adhanAt(Prayer.ISHA))
        assertEquals(ZoneId.of("Asia/Dubai"), aug14.zone)
    }

    @Test
    fun `never sets Iqama times from the API`() = runTest {
        enqueueFixture()

        val schedules = client.annualCalendar(alAin, 2026, CalculationSettings())

        assertTrue(
            schedules.all { day -> day.times.all { it.iqama == null } },
            "Iqama is derived from user settings and must not be baked into cached API data",
        )
    }

    @Test
    fun `requests coordinates, never a city, and always asks for ISO timestamps`() = runTest {
        enqueueFixture()

        client.annualCalendar(alAin, 2026, CalculationSettings())
        val url = server.takeRequest().target

        assertTrue(url.contains("latitude=24.2075"), url)
        assertTrue(url.contains("longitude=55.7447"), url)
        assertTrue(url.contains("iso8601=true"), url)
        assertTrue(url.contains("annual=true"), url)
        assertTrue(url.contains("method=16"), "The configured default must be sent: $url")
        // The city endpoints depend on 7x.ax geocoding, which shut down on 2026-09-01.
        assertTrue(!url.contains("city="), "Must never geocode by city: $url")
        assertTrue(!url.contains("x7xapikey"), "Must never send a 7x key: $url")
    }

    @Test
    fun `sends shafaq only for the method where it has any effect`() = runTest {
        enqueueFixture()
        client.annualCalendar(alAin, 2026, CalculationSettings(methodId = 16))
        assertTrue(!server.takeRequest().target.contains("shafaq"), "Irrelevant for method 16")

        enqueueFixture()
        client.annualCalendar(alAin, 2026, CalculationSettings(methodId = 15))
        assertTrue(server.takeRequest().target.contains("shafaq=general"), "Relevant for Moonsighting")
    }

    @Test
    fun `never sends tune, because corrections are applied at read time`() = runTest {
        enqueueFixture()
        client.annualCalendar(
            alAin, 2026,
            CalculationSettings(
                adjustments = PrayerAdjustments(sunrise = -4, asr = 1),
            ),
        )

        // Corrections must reach a mosque's published table too, and no request parameter can shift
        // that — so they are applied once, on read, by PrayerAdjustments.shiftedBy. Sending `tune`
        // here as well would double every correction on the calculated path only.
        val url = server.takeRequest().target
        assertTrue(!url.contains("tune"), "Corrections belong at read time, not in the request: $url")
    }

    @Test
    fun `passes the selected school through`() = runTest {
        enqueueFixture()
        client.annualCalendar(alAin, 2026, CalculationSettings(school = School.HANAFI))
        assertTrue(server.takeRequest().target.contains("school=1"))
    }

    @Test
    fun `surfaces server errors so the caller can retry`() = runTest {
        server.enqueue(MockResponse(code = 503, body = "unavailable"))

        // Silently returning an empty list would let the sync worker record success and cache
        // nothing, leaving the watch on estimates forever.
        assertFailsWith<IOException> {
            client.annualCalendar(alAin, 2026, CalculationSettings())
        }
    }
}
