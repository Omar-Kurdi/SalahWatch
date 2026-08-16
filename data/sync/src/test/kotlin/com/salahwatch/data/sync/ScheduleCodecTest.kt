package com.salahwatch.data.sync

import com.salahwatch.core.model.DaySchedule
import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.PrayerTime
import com.salahwatch.core.model.ScheduleSource
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleCodecTest {

    private val dubai = ZoneId.of("Asia/Dubai")

    private fun day(date: LocalDate, zone: ZoneId = dubai, source: ScheduleSource = ScheduleSource.ALADHAN) =
        DaySchedule(
            date = date,
            zone = zone,
            times = listOf(
                Prayer.FAJR to 4L, Prayer.SUNRISE to 6L, Prayer.DHUHR to 12L,
                Prayer.ASR to 15L, Prayer.MAGHRIB to 18L, Prayer.ISHA to 20L,
            ).map { (prayer, hour) ->
                PrayerTime(prayer, date.atStartOfDay(zone).toInstant().plusSeconds(hour * 3600), null)
            },
            source = source,
        )

    private fun days(count: Int, from: LocalDate = LocalDate.of(2026, 8, 14)) =
        (0 until count).map { day(from.plusDays(it.toLong())) }

    @Test
    fun `round-trips a schedule exactly`() {
        val original = days(30)
        val decoded = assertNotNull(ScheduleCodec.decode(ScheduleCodec.encode(original)))

        assertEquals(original.size, decoded.size)
        assertEquals(original, decoded, "Encoding must be lossless at one-second resolution")
    }

    @Test
    fun `preserves the timezone`() {
        val london = days(3).map { it.copy(zone = ZoneId.of("Europe/London")) }
        val decoded = assertNotNull(ScheduleCodec.decode(ScheduleCodec.encode(london)))

        assertEquals(ZoneId.of("Europe/London"), decoded.first().zone)
    }

    @Test
    fun `preserves the source so estimates stay marked`() {
        val estimates = days(2).map { it.copy(source = ScheduleSource.LOCAL_ESTIMATE) }
        val decoded = assertNotNull(ScheduleCodec.decode(ScheduleCodec.encode(estimates)))

        assertTrue(decoded.all { it.source == ScheduleSource.LOCAL_ESTIMATE })
    }

    @Test
    fun `a year fits comfortably inside the Data Layer limit`() {
        val encoded = ScheduleCodec.encode(days(365))

        assertTrue(
            encoded.size < ScheduleCodec.MAX_PAYLOAD_BYTES,
            "A year is ${encoded.size} bytes against a ${ScheduleCodec.MAX_PAYLOAD_BYTES} limit",
        )
        // Guards the format against silently bloating: JSON of the same data is roughly 40 KB.
        assertTrue(encoded.size < 20_000, "Expected under 20 KB, was ${encoded.size}")
    }

    @Test
    fun `predicts its own encoded size`() {
        val encoded = ScheduleCodec.encode(days(120))
        assertEquals(encoded.size, ScheduleCodec.encodedSize(120, dubai.id))
    }

    @Test
    fun `rejects a truncated payload rather than throwing`() {
        val encoded = ScheduleCodec.encode(days(10))
        // A listener service must not crash because a transfer was cut short.
        assertNull(ScheduleCodec.decode(encoded.copyOf(encoded.size / 2)))
    }

    @Test
    fun `rejects a payload from a newer version`() {
        val encoded = ScheduleCodec.encode(days(5))
        encoded[0] = 99
        // The phone may update before the watch; the watch falls back rather than misreading.
        assertNull(ScheduleCodec.decode(encoded))
    }

    @Test
    fun `rejects arbitrary bytes`() {
        assertNull(ScheduleCodec.decode(ByteArray(0)))
        assertNull(ScheduleCodec.decode(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `never carries Iqama times`() {
        val decoded = assertNotNull(ScheduleCodec.decode(ScheduleCodec.encode(days(3))))
        assertTrue(
            decoded.all { day -> day.times.all { it.iqama == null } },
            "Iqama is derived from settings on the watch; sending it would risk two sources of truth",
        )
    }

    @Test
    fun `survives a DST transition without shifting times`() {
        val london = ZoneId.of("Europe/London")
        val original = (24..26).map { day(LocalDate.of(2026, 10, it), zone = london) }
        val decoded = assertNotNull(ScheduleCodec.decode(ScheduleCodec.encode(original)))

        // Absolute instants carry their own offset, so the boundary needs no special handling.
        assertEquals(original.map { it.times.map(PrayerTime::adhan) }, decoded.map { it.times.map(PrayerTime::adhan) })
    }

    @Test
    fun `requires a single timezone per payload`() {
        val mixed = listOf(
            day(LocalDate.of(2026, 8, 14)),
            day(LocalDate.of(2026, 8, 15), zone = ZoneId.of("Europe/London")),
        )
        val error = runCatching { ScheduleCodec.encode(mixed) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException, "Mixed zones must fail loudly at encode time")
    }
}

