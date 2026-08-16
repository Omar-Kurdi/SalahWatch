package com.salahwatch.core.calculation

import com.salahwatch.core.model.IqamaMode
import com.salahwatch.core.model.IqamaSettings
import com.salahwatch.core.model.MosqueIqamaOffsets
import com.salahwatch.core.model.MosqueIqamaTimes
import com.salahwatch.core.model.Prayer
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Iqama derivation, and in particular the two forms a mosque may publish in.
 *
 * Mawaqit returns either offsets (`"+15"`) or wall-clock times (`"12:46"`) in the same field, so
 * both have to work. The distinction is not cosmetic: an offset tracks the Adhan through the year
 * and a fixed time does not, so treating one as the other would be wrong on every day but the one
 * it was read on.
 */
class IqamaCalculatorTest {

    private val date = LocalDate.of(2026, 8, 15)
    private val schedule = daySchedule(date, iqama = null)

    private fun iqamaFor(prayer: Prayer, applied: com.salahwatch.core.model.DaySchedule) =
        applied[prayer]?.iqama?.atZone(DUBAI)?.toLocalTime()?.toString()

    @Test
    fun `uses the user's own offsets by default`() {
        val applied = IqamaCalculator.apply(schedule, IqamaSettings())

        // Fajr 04:30 + the SPEC default of 25 minutes.
        assertEquals("04:55", iqamaFor(Prayer.FAJR, applied))
    }

    @Test
    fun `uses the mosque's offsets when asked for them`() {
        val applied = IqamaCalculator.apply(
            schedule = schedule,
            settings = IqamaSettings(mode = IqamaMode.FROM_MOSQUE),
            mosqueOffsets = MosqueIqamaOffsets(fajr = 25, dhuhr = 15, asr = 15, maghrib = 8, isha = 15),
        )

        assertEquals("12:40", iqamaFor(Prayer.DHUHR, applied), "Dhuhr 12:25 + the mosque's 15")
    }

    @Test
    fun `uses the mosque's published times when it publishes times rather than offsets`() {
        val applied = IqamaCalculator.apply(
            schedule = schedule,
            settings = IqamaSettings(mode = IqamaMode.FROM_MOSQUE),
            mosqueTimes = MosqueIqamaTimes("04:56", "12:46", "16:19", "19:04", "20:31"),
        )

        assertEquals("04:56", iqamaFor(Prayer.FAJR, applied))
        assertEquals("12:46", iqamaFor(Prayer.DHUHR, applied))
        assertEquals("20:31", iqamaFor(Prayer.ISHA, applied))
    }

    @Test
    fun `prefers published times over published offsets`() {
        val applied = IqamaCalculator.apply(
            schedule = schedule,
            settings = IqamaSettings(mode = IqamaMode.FROM_MOSQUE),
            mosqueOffsets = MosqueIqamaOffsets(fajr = 25, dhuhr = 15, asr = 15, maghrib = 8, isha = 15),
            mosqueTimes = MosqueIqamaTimes("04:56", "12:46", "16:19", "19:04", "20:31"),
        )

        // A time says when the congregation starts; an offset only says how long after the Adhan.
        assertEquals("12:46", iqamaFor(Prayer.DHUHR, applied))
    }

    @Test
    fun `falls back to the user's offsets when the mosque publishes neither`() {
        val applied = IqamaCalculator.apply(
            schedule = schedule,
            settings = IqamaSettings(mode = IqamaMode.FROM_MOSQUE),
        )

        // Silently producing no Iqama in this mode would look like a bug rather than a fallback.
        assertEquals("04:55", iqamaFor(Prayer.FAJR, applied))
    }

    @Test
    fun `reads a congregation timed before its own Adhan as the next day`() {
        // A late Isha is the case this exists for: called at 23:50, congregation at 00:10, which is
        // twenty minutes later and on tomorrow's date. Note the example matters — an Iqama four
        // hours after its Adhan is not a midnight crossing, it is bad data, and the sanity cap
        // below rejects it.
        val lateIsha = daySchedule(date, isha = "23:50", iqama = null)

        val applied = IqamaCalculator.apply(
            schedule = lateIsha,
            settings = IqamaSettings(mode = IqamaMode.FROM_MOSQUE),
            mosqueTimes = MosqueIqamaTimes("04:56", "12:46", "16:19", "19:04", "00:10"),
        )

        val isha = checkNotNull(applied[Prayer.ISHA]?.iqama).atZone(DUBAI)
        assertEquals(date.plusDays(1), isha.toLocalDate(), "00:10 after a 23:50 Isha is tomorrow")
        assertEquals("00:10", isha.toLocalTime().toString())
    }

    @Test
    fun `keeps Sunrise free of a congregation whatever the mosque publishes`() {
        val applied = IqamaCalculator.apply(
            schedule = schedule,
            settings = IqamaSettings(mode = IqamaMode.FROM_MOSQUE),
            mosqueTimes = MosqueIqamaTimes("04:56", "12:46", "16:19", "19:04", "20:31"),
        )

        assertNull(applied[Prayer.SUNRISE]?.iqama)
    }

    @Test
    fun `rejects a published time too far from its own Adhan to be real`() {
        // Dhuhr is 12:25 here. A mistyped 12:00 is before its Adhan, so the next-day rule would
        // otherwise read it as a congregation twenty-three hours away — a value that passes every
        // type check and is nonsense on screen. The same guard is what stops a 00:15 Iqama being
        // attached to a 20:13 Isha four hours earlier.
        val applied = IqamaCalculator.apply(
            schedule = schedule,
            settings = IqamaSettings(mode = IqamaMode.FROM_MOSQUE),
            mosqueTimes = MosqueIqamaTimes("04:56", "12:00", "16:19", "19:04", "20:31"),
        )

        assertEquals("12:45", iqamaFor(Prayer.DHUHR, applied), "Falls back to the user's own offset")
        assertEquals("16:19", iqamaFor(Prayer.ASR, applied), "The sane fields still stand")
    }

    @Test
    fun `accepts an unusually long but plausible wait`() {
        // The cap must not reject a mosque that simply waits a while. Isha at 20:13 with a
        // congregation at 21:30 is an hour and seventeen minutes — longer than any default this app
        // ships, well inside two hours, and entirely legitimate.
        val applied = IqamaCalculator.apply(
            schedule = schedule,
            settings = IqamaSettings(mode = IqamaMode.FROM_MOSQUE),
            mosqueTimes = MosqueIqamaTimes("04:56", "12:46", "16:19", "19:04", "21:30"),
        )

        assertEquals("21:30", iqamaFor(Prayer.ISHA, applied))
    }

    @Test
    fun `ignores an unparseable published time rather than losing the day`() {
        val applied = IqamaCalculator.apply(
            schedule = schedule,
            settings = IqamaSettings(mode = IqamaMode.FROM_MOSQUE),
            mosqueTimes = MosqueIqamaTimes("not a time", "12:46", "16:19", "19:04", "20:31"),
        )

        // The bad field costs that prayer its mosque time and no more; the rest still stand.
        assertEquals("04:55", iqamaFor(Prayer.FAJR, applied), "Falls back to the user's own offset")
        assertEquals("12:46", iqamaFor(Prayer.DHUHR, applied))
    }
}
