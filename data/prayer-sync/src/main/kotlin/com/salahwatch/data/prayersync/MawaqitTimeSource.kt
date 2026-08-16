package com.salahwatch.data.prayersync

import android.util.Log
import com.salahwatch.core.calculation.LocalPrayerCalculator
import com.salahwatch.core.model.CalculationSettings
import com.salahwatch.core.model.DaySchedule
import com.salahwatch.core.model.IqamaSettings
import com.salahwatch.core.model.MosqueRef
import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.PrayerLocation
import java.io.IOException
import java.time.LocalDate

/**
 * A mosque's published year, validated before it is trusted.
 *
 * The validation is the point. Mosque timetables are entered by people and the data reflects that:
 * some publish seven times a day instead of six, and at least one Istanbul mosque declares itself in
 * `Asia/Jerusalem`. Either would produce a schedule that is confidently, silently wrong.
 *
 * So every calendar is checked against astronomy before use. Sunrise is the anchor because it is the
 * one prayer time that depends on nothing but where and when — no method, no convention, no
 * authority — which makes it computable exactly and therefore usable as ground truth for whether the
 * rest of the mosque's data lines up.
 */
public class MawaqitTimeSource(
    private val client: MawaqitClient = MawaqitClient(),
    private val calculator: LocalPrayerCalculator = LocalPrayerCalculator(),
) : MosqueTimeSource {

    override suspend fun calendarFor(
        mosque: MosqueRef,
        location: PrayerLocation,
    ): List<DaySchedule>? {
        val calendar = try {
            client.calendar(mosque)
        } catch (e: IOException) {
            Log.w(TAG, "Could not read ${mosque.slug}; falling back to calculation", e)
            return null
        }

        val parser = MosqueCalendarParser { date -> astronomicalSunrise(location, date) }

        return when (val result = parser.parse(calendar.days, calendar.zone)) {
            is MosqueCalendarResult.Usable -> result.schedules

            is MosqueCalendarResult.Rejected -> {
                // Loud, because this means a mosque's published data is wrong and the user is
                // silently getting calculated times instead of the ones they chose.
                Log.w(TAG, "Rejected ${mosque.slug}: ${result.problem}")
                null
            }
        }
    }

    /** Sunrise for the location, from the offline calculator — no network, no method dependence. */
    private fun astronomicalSunrise(location: PrayerLocation, date: LocalDate) =
        runCatching {
            calculator.calculate(location, date, CalculationSettings(), IqamaSettings())[Prayer.SUNRISE]?.adhan
        }.getOrNull()

    private companion object {
        const val TAG = "MawaqitTimeSource"
    }
}
