package com.salahwatch.data.prayersync

import com.salahwatch.core.model.CalculationSettings
import com.salahwatch.core.model.DaySchedule
import com.salahwatch.data.prayertimes.PrayerScheduleRepository
import com.salahwatch.data.prayertimes.ScheduleCache
import com.salahwatch.data.prayertimes.ScheduleCacheKey
import com.salahwatch.core.model.PrayerLocation
import java.io.IOException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * Fetches prayer times from AlAdhan.
 *
 * Two deliberate constraints:
 *
 * **Coordinates only, never city names.** The `timingsByCity` endpoints depend on geocoding through
 * 7x.ax, which shut down on 2026-09-01, and they already returned masked coordinates without a key.
 * Every request here is by latitude/longitude, which needs no key at all. See RESEARCH.md §2.1(B).
 *
 * **A whole year in one request.** `annual=true` returns 365 days for roughly 500 KB in about a
 * second. One call per location per year is what lets the app work offline indefinitely and keeps
 * SPEC.md's "cache as much data as possible to avoid multiple API calls" from being a compromise.
 */
public class AlAdhanClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : PrayerTimeSource {
    /**
     * Every day of [year] for [location], ordered by date.
     *
     * @throws IOException on network failure or a non-2xx response, so callers — the sync worker —
     *   can retry rather than silently cache nothing.
     */
    override suspend fun annualCalendar(
        location: PrayerLocation,
        year: Int,
        settings: CalculationSettings,
    ): List<DaySchedule> {
        val url = baseUrl.newBuilder()
            .addPathSegment("calendar")
            .addPathSegment(year.toString())
            .applyCommonParameters(location, settings)
            .addQueryParameter("annual", "true")
            .build()

        val body = get(url)
        val response = json.decodeFromString<CalendarResponse<AnnualData>>(body)

        return response.data
            .entries
            .sortedBy { it.key.toIntOrNull() ?: 0 }
            .flatMap { (_, days) -> days }
            .map { it.toDaySchedule() }
            .sortedBy { it.date }
    }

    /**
     * One month, for a cheaper refresh when a full year is not needed.
     *
     * @throws IOException as [annualCalendar].
     */
    public suspend fun monthCalendar(
        location: PrayerLocation,
        year: Int,
        month: Int,
        settings: CalculationSettings,
    ): List<DaySchedule> {
        val url = baseUrl.newBuilder()
            .addPathSegment("calendar")
            .addPathSegment(year.toString())
            .addPathSegment(month.toString())
            .applyCommonParameters(location, settings)
            .build()

        val body = get(url)
        val response = json.decodeFromString<CalendarResponse<List<CalendarDay>>>(body)

        return response.data.map { it.toDaySchedule() }.sortedBy { it.date }
    }

    /**
     * The parameters every request shares.
     *
     * `iso8601=true` is not optional: without it, timings come back as bare wall-clock strings and
     * the DST boundary becomes ambiguous. With it, each timestamp carries its own UTC offset.
     */
    private fun HttpUrl.Builder.applyCommonParameters(
        location: PrayerLocation,
        settings: CalculationSettings,
    ): HttpUrl.Builder = apply {
        addQueryParameter("latitude", location.latitude.toString())
        addQueryParameter("longitude", location.longitude.toString())
        addQueryParameter("method", settings.methodId.toString())
        addQueryParameter("school", settings.school.apiValue.toString())
        addQueryParameter("iso8601", "true")

        // `tune` is deliberately not sent. Corrections are applied once, at read time, by
        // com.salahwatch.core.model.shiftedBy, so that they reach a mosque's published
        // table as well as these calculated times — a mosque publishes a fixed table that no
        // request parameter can shift. Sending it here as well would double every correction.

        // Only meaningful for the Moonsighting method; sent only when it actually applies so the
        // request URL reflects what is really in play.
        if (settings.isShafaqRelevant) {
            addQueryParameter("shafaq", settings.shafaq.apiValue)
        }
    }

    private suspend fun get(url: HttpUrl): String = httpClient.getBody(url)

    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://api.aladhan.com/v1/"
    }
}

/*
 * There was a `toTuneParameter()` here, rendering corrections into AlAdhan's `tune` field. It was
 * removed rather than left unused, because the parameter it built cannot do the job: `tune` shifts
 * what the API calculates, and a mosque's published table is not calculated by anything. Corrections
 * are applied once on read instead — see com.salahwatch.core.model.shiftedBy.
 *
 * Recorded here in case the parameter is ever wanted again: the field order is undocumented in the
 * obvious places and was confirmed against the live API by tuning one field at a time and reading
 * back `meta.offset` — Imsak, Fajr, Sunrise, Dhuhr, Asr, Maghrib, Sunset, Isha, Midnight.
 */
