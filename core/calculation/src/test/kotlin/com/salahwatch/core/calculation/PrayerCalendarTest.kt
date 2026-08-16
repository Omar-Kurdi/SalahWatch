package com.salahwatch.core.calculation

import com.salahwatch.core.model.IqamaSettings
import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.ScheduleSource
import java.time.Duration
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrayerCalendarTest {

    private val day1 = LocalDate.of(2026, 8, 14)
    private val day2 = LocalDate.of(2026, 8, 15)
    private val calendar = PrayerCalendar.of(listOf(daySchedule(day1), daySchedule(day2)))

    @Test
    fun `next prayer at midday is Asr`() {
        val next = calendar.nextPrayer(at(day1, DUBAI, "12:30"))
        assertEquals(Prayer.ASR, next?.prayer)
        assertEquals(at(day1, DUBAI, "15:49"), next?.adhan)
    }

    @Test
    fun `sunrise is never returned as the next prayer`() {
        // 04:45 sits between Fajr (04:30) and Sunrise (05:52); the next *prayer* is Dhuhr.
        val next = calendar.nextPrayer(at(day1, DUBAI, "04:45"))
        assertEquals(Prayer.DHUHR, next?.prayer)
    }

    @Test
    fun `sunrise is never the current prayer`() {
        // 06:00 is after Sunrise but the prayer in effect is still Fajr.
        val current = calendar.currentPrayer(at(day1, DUBAI, "06:00"))
        assertEquals(Prayer.FAJR, current?.prayer)
    }

    @Test
    fun `sunrise is still reachable through its own accessor`() {
        val sunrise = calendar.nextOccurrenceOf(Prayer.SUNRISE, at(day1, DUBAI, "01:00"))
        assertEquals(at(day1, DUBAI, "05:52"), sunrise?.adhan)
    }

    @Test
    fun `after Isha the next prayer is tomorrow's Fajr`() {
        val next = calendar.nextPrayer(at(day1, DUBAI, "21:00"))
        assertEquals(Prayer.FAJR, next?.prayer)
        assertEquals(day2, next?.date)
        assertEquals(at(day2, DUBAI, "04:30"), next?.adhan)
    }

    @Test
    fun `at an Adhan instant the prayer is current, not next`() {
        val asr = at(day1, DUBAI, "15:49")
        assertEquals(Prayer.ASR, calendar.currentPrayer(asr)?.prayer)
        assertEquals(Prayer.MAGHRIB, calendar.nextPrayer(asr)?.prayer)
    }

    @Test
    fun `current prayer is null before the first Adhan in the window`() {
        assertNull(calendar.currentPrayer(at(day1, DUBAI, "02:00")))
    }

    @Test
    fun `next prayer is null past the end of loaded data`() {
        // The caller must read this as "cache exhausted", not "no more prayers".
        assertNull(calendar.nextPrayer(at(day2, DUBAI, "23:59")))
    }

    @Test
    fun `next Iqama can belong to the current prayer while next prayer has moved on`() {
        // 15:55 is after Asr's Adhan (15:49) but before its Iqama (16:09, +20).
        val instant = at(day1, DUBAI, "15:55")
        assertEquals(Prayer.MAGHRIB, calendar.nextPrayer(instant)?.prayer)
        assertEquals(Prayer.ASR, calendar.nextIqama(instant)?.prayer)
    }

    @Test
    fun `Isha Iqama crossing midnight is still found in order`() {
        // Isha at 23:50 with a +20 offset puts the Iqama at 00:10 the next day.
        val lateIsha = PrayerCalendar.of(
            listOf(
                daySchedule(day1, isha = "23:50"),
                daySchedule(day2),
            ),
        )
        val justBeforeMidnight = at(day1, DUBAI, "23:55")
        val iqama = lateIsha.nextIqama(justBeforeMidnight)

        assertEquals(Prayer.ISHA, iqama?.prayer)
        assertEquals(day1, iqama?.date, "The Iqama belongs to day 1 even though it falls on day 2")
        assertEquals(at(day2, DUBAI, "00:10"), iqama?.iqama)
        // And the next prayer is correctly tomorrow's Fajr, not tomorrow's Isha.
        assertEquals(Prayer.FAJR, lateIsha.nextPrayer(justBeforeMidnight)?.prayer)
    }

    @Test
    fun `later schedules replace earlier ones for the same day`() {
        val estimate = daySchedule(day1, source = ScheduleSource.LOCAL_ESTIMATE, asr = "15:52")
        val authoritative = daySchedule(day1, source = ScheduleSource.ALADHAN, asr = "15:49")

        val merged = PrayerCalendar.of(listOf(estimate, authoritative))
        val asr = merged.nextOccurrenceOf(Prayer.ASR, at(day1, DUBAI, "13:00"))

        assertEquals(at(day1, DUBAI, "15:49"), asr?.adhan)
        assertEquals(ScheduleSource.ALADHAN, asr?.source)
    }

    @Test
    fun `unsorted input is normalised`() {
        val reversed = PrayerCalendar.of(listOf(daySchedule(day2), daySchedule(day1)))
        val adhans = reversed.occurrences.map { it.adhan }
        assertEquals(adhans.sorted(), adhans)
    }

    @Test
    fun `empty calendar answers safely`() {
        val empty = PrayerCalendar.empty()
        assertTrue(empty.isEmpty)
        assertNull(empty.nextPrayer(at(day1, DUBAI, "12:00")))
        assertNull(empty.currentPrayer(at(day1, DUBAI, "12:00")))
        assertFalse(empty.covers(at(day1, DUBAI, "12:00")))
    }

    @Test
    fun `coverage reports the window the calendar can answer for`() {
        assertTrue(calendar.covers(at(day1, DUBAI, "12:00")))
        assertFalse(calendar.covers(at(day1, DUBAI, "02:00")))
        assertFalse(calendar.covers(at(day2, DUBAI, "23:00")))
    }

    @Test
    fun `Iqama offsets follow the SPEC defaults`() {
        val schedule = daySchedule(day1, iqama = IqamaSettings())
        fun offsetOf(prayer: Prayer): Long {
            val time = assertNotNull(schedule[prayer])
            val iqama = assertNotNull(time.iqama, "${prayer.shortName} should have an Iqama")
            return Duration.between(time.adhan, iqama).toMinutes()
        }

        assertEquals(25, offsetOf(Prayer.FAJR))
        assertEquals(20, offsetOf(Prayer.DHUHR))
        assertEquals(20, offsetOf(Prayer.ASR))
        assertEquals(8, offsetOf(Prayer.MAGHRIB))
        assertEquals(20, offsetOf(Prayer.ISHA))
        assertNull(schedule[Prayer.SUNRISE]?.iqama, "Sunrise must never have an Iqama")
    }
}
