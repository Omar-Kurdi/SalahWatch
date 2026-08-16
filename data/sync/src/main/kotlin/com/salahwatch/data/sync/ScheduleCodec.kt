package com.salahwatch.data.sync

import android.util.Log
import com.salahwatch.core.model.DaySchedule
import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.PrayerTime
import com.salahwatch.core.model.ScheduleSource

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Packs prayer schedules into bytes for the Data Layer.
 *
 * A hand-rolled binary format rather than JSON, for one concrete reason: a `DataItem` payload is
 * **limited to 100 KB**. The same 120 days is roughly 6 KB packed and around 40 KB as JSON — the
 * difference between comfortably inside the limit and uncomfortably near it, on a payload that grows
 * with every day of lookahead added.
 *
 * Instants are stored as epoch **seconds**, not millis: prayer times have one-minute resolution, so
 * millisecond precision would be three bytes per value of noise.
 *
 * Format (all big-endian):
 * ```
 * u8   version
 * u16  zoneId length, then that many UTF-8 bytes
 * u16  day count
 * per day:
 *   i32  epochDay
 *   u8   source ordinal
 *   6 x i64  epoch seconds, in Prayer.entries order
 * ```
 * Iqama times are absent by design: they are derived from user settings, which travel separately, so
 * sending them would duplicate a value the watch can compute and risk the two disagreeing.
 */
public object ScheduleCodec {

    private const val VERSION: Byte = 1
    private const val PRAYERS_PER_DAY = 6
    private const val BYTES_PER_DAY = 4 + 1 + PRAYERS_PER_DAY * 8

    /** Encodes [schedules], which must be non-empty and share one timezone. */
    public fun encode(schedules: List<DaySchedule>): ByteArray {
        require(schedules.isNotEmpty()) { "Nothing to encode" }
        val zone = schedules.first().zone
        require(schedules.all { it.zone == zone }) {
            "All schedules must share a timezone; the payload stores it once"
        }

        val zoneBytes = zone.id.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(
            1 + 2 + zoneBytes.size + 2 + schedules.size * BYTES_PER_DAY,
        )

        buffer.put(VERSION)
        buffer.putShort(zoneBytes.size.toShort())
        buffer.put(zoneBytes)
        buffer.putShort(schedules.size.toShort())

        for (day in schedules) {
            buffer.putInt(day.date.toEpochDay().toInt())
            buffer.put(day.source.ordinal.toByte())
            for (prayer in Prayer.entries) {
                val time = requireNotNull(day[prayer]) {
                    "Schedule for ${day.date} is missing ${prayer.shortName}"
                }
                buffer.putLong(time.adhan.epochSecond)
            }
        }

        return buffer.array()
    }

    /**
     * Decodes a payload, or returns null if it is malformed or from a future version.
     *
     * Returning null rather than throwing is deliberate: this data arrives from another device that
     * may be running a newer build of the app. The watch must degrade to its own cached or
     * calculated times, never crash a listener service because the phone got upgraded first.
     */
    @Suppress("ReturnCount")
    public fun decode(bytes: ByteArray): List<DaySchedule>? {
        return try {
            val buffer = ByteBuffer.wrap(bytes)
            if (buffer.get() != VERSION) return null

            val zoneBytes = ByteArray(buffer.short.toInt())
            buffer.get(zoneBytes)
            val zone = ZoneId.of(String(zoneBytes, Charsets.UTF_8))
            val count = buffer.short.toInt()

            (0 until count).map {
                val date = LocalDate.ofEpochDay(buffer.int.toLong())
                val sourceOrdinal = buffer.get().toInt()
                val source = ScheduleSource.entries.getOrNull(sourceOrdinal)
                    ?: error("Unknown schedule source ordinal $sourceOrdinal")
                val times = Prayer.entries.map { prayer ->
                    PrayerTime(prayer, Instant.ofEpochSecond(buffer.long), null)
                }
                DaySchedule(date = date, zone = zone, times = times, source = source)
            }
        } catch (e: BufferUnderflowException) {
            Log.w(TAG, "Truncated schedule payload; keeping existing data", e)
            null
        } catch (e: IllegalStateException) {
            // An unrecognised source ordinal, from a build that added one.
            Log.w(TAG, "Unrecognised schedule payload; keeping existing data", e)
            null
        } catch (e: IllegalArgumentException) {
            // An unknown zone id, or a domain invariant DaySchedule enforces. Either way the payload
            // cannot be trusted, and the watch is better off with what it already has.
            Log.w(TAG, "Invalid schedule payload; keeping existing data", e)
            null
        }
    }

    /** The encoded size of [dayCount] days, for checking against the Data Layer's 100 KB limit. */
    public fun encodedSize(dayCount: Int, zoneId: String): Int =
        1 + 2 + zoneId.toByteArray(Charsets.UTF_8).size + 2 + dayCount * BYTES_PER_DAY

    private const val TAG = "ScheduleCodec"

    /** The Data Layer's hard payload limit. */
    public const val MAX_PAYLOAD_BYTES: Int = 100 * 1024
}
