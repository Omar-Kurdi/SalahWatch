package com.salahwatch.data.prayersync

import com.salahwatch.core.model.MosqueIqamaOffsets
import com.salahwatch.core.model.MosqueIqamaTimes
import com.salahwatch.core.model.PrayerLocation
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * The mosque search, and specifically the shape of its `iqama` field.
 *
 * The bodies here are trimmed from real search responses. They are worth pinning because the field
 * is polymorphic in a way nothing documents: neighbouring mosques in the same city return offsets
 * and wall-clock times in the same key. Reading only the offset form makes "the mosque's offsets"
 * silently do nothing for half of them — every field fails to parse, the whole set is dropped, and
 * the app falls back to the user's own offsets with no sign that it has.
 */
class MawaqitSearchTest {

    private lateinit var server: MockWebServer
    private lateinit var client: MawaqitClient

    private val somewhere = PrayerLocation(24.2075, 55.7447, ZoneId.of("Asia/Dubai"))

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = MawaqitClient(baseUrl = server.url("/").toString().toHttpUrl())
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    private fun enqueue(body: String) {
        server.enqueue(MockResponse(code = 200, body = body))
    }

    private fun mosque(
        uuid: String = "b547c0b6",
        slug: String = "masjid-madinat-al-sheikh-tahnoun",
        name: String = "Masjid Madinat Al Sheikh Tahnoun",
        iqama: String = """["+25","+15","+15","+8","+15"]""",
        extra: String = "",
    ) = """
        {"uuid":"$uuid","slug":"$slug","name":"$name","localisation":"Example City",
         "proximity":2624,"iqama":$iqama$extra}
    """.trimIndent()

    @Test
    fun `reads iqama published as offsets`() = runTest {
        enqueue("[${mosque()}]")

        val found = client.nearby(somewhere).single()

        assertEquals(MosqueIqamaOffsets(25, 15, 15, 8, 15), found.iqamaOffsets)
        assertNull(found.iqamaTimes, "Offsets and times are alternatives, never both")
    }

    @Test
    fun `reads iqama published as wall-clock times`() = runTest {
        enqueue("""[${mosque(iqama = """["04:56","12:46","16:19","19:04","20:31"]""")}]""")

        val found = client.nearby(somewhere).single()

        assertEquals(
            MosqueIqamaTimes("04:56", "12:46", "16:19", "19:04", "20:31"),
            found.iqamaTimes,
        )
        assertNull(found.iqamaOffsets, "A time is not an offset; parsing it as one would give zero")
    }

    @Test
    fun `ignores iqama a mosque has switched off`() = runTest {
        enqueue("[${mosque(extra = ""","iqamaEnabled":false""")}]")

        val found = client.nearby(somewhere).single()

        // The field still carries whatever was last configured. Showing it would present a
        // congregation time the mosque has deliberately stopped publishing.
        assertNull(found.iqamaOffsets)
        assertNull(found.iqamaTimes)
    }

    @Test
    fun `drops a partly published iqama rather than mixing two schedules`() = runTest {
        enqueue("""[${mosque(iqama = """["+25","","+15","+8","+15"]""")}]""")

        val found = client.nearby(somewhere).single()

        assertNull(found.iqamaOffsets, "All five or none — a partial set is a schedule nobody chose")
        assertNull(found.iqamaTimes)
    }

    @Test
    fun `lists the same mosque once`() = runTest {
        enqueue("[${mosque()},${mosque()}]")

        assertEquals(1, client.nearby(somewhere).size)
    }

    @Test
    fun `keeps mosques that merely share a name`() = runTest {
        // Two genuinely different mosques, same name. Merging them would silently follow one
        // mosque's calendar while displaying the other's — so only the uuid may deduplicate.
        enqueue("[${mosque(uuid = "aaa", slug = "one")},${mosque(uuid = "bbb", slug = "two")}]")

        assertEquals(2, client.nearby(somewhere).size)
    }
}
