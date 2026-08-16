package com.salahwatch.data.prayersync

import com.salahwatch.core.model.MosqueIqamaOffsets
import com.salahwatch.core.model.MosqueIqamaTimes
import com.salahwatch.core.model.MosqueRef
import com.salahwatch.core.model.PrayerLocation
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/** A mosque's published year, as raw strings, plus what is needed to interpret them. */
public data class MosqueCalendar(
    val zone: ZoneId,
    /** Times per date, exactly as the mosque published them. */
    val days: Map<LocalDate, List<String>>,
)

/**
 * Finds mosques near a location and reads their published prayer times, via Mawaqit.
 *
 * Mawaqit is the platform mosques use to publish their own timetables, which is why this is worth
 * having: where a mosque follows its national timetable, its times *are* that timetable rather than
 * an approximation of it. Checked against an official national timetable for a month, a nearby
 * mosque matched all six times on all 31 days, including the Sunrise that no calculation method
 * reproduces.
 *
 * Two things this deliberately does not do. It never polls: a mosque's year is fetched once and
 * cached, the same as the calculated path. And it never trusts what it reads — see
 * [MosqueCalendarParser], which checks the data against astronomy before any of it is shown.
 */
public class MawaqitClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /**
     * Mosques near [location], nearest first.
     *
     * @throws IOException on network failure, so the caller can retry rather than show an empty list
     *   as though there were genuinely no mosques nearby.
     */
    public suspend fun nearby(location: PrayerLocation): List<MosqueRef> {
        val url = baseUrl.newBuilder()
            .addPathSegments("api/2.0/mosque/search")
            .addQueryParameter("lat", location.latitude.toString())
            .addQueryParameter("lon", location.longitude.toString())
            .build()

        // Distinct by uuid because the directory is a public dataset and nothing guarantees it
        // cannot list the same mosque twice; a picker that offers the same name twice looks broken
        // even when both entries would work. Deliberately *not* distinct by name: two mosques can
        // legitimately share one, and merging them would silently follow the wrong calendar.
        return json.parseToJsonElement(get(url)).jsonArray
            .mapNotNull { it.toMosqueRef() }
            .distinctBy { it.id }
    }

    /**
     * A mosque's whole published year.
     *
     * Read from the mosque's own page rather than the REST API, because the prayer-times endpoint
     * requires credentials this app does not have, while the page embeds the same calendar the
     * mosque's own display uses.
     *
     * @throws IOException on network failure or if the page carries no calendar.
     */
    public suspend fun calendar(mosque: MosqueRef): MosqueCalendar {
        val url = baseUrl.newBuilder().addPathSegments("en/${mosque.slug}").build()
        val conf = extractConfig(get(url))
            ?: throw IOException("No calendar published for ${mosque.slug}")

        val zone = conf["timezone"]?.jsonPrimitive?.contentOrNullSafe()
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.systemDefault()

        val months = conf["calendar"]?.jsonArray
            ?: throw IOException("No calendar published for ${mosque.slug}")

        val year = LocalDate.now(zone).year
        val days = buildMap {
            months.forEachIndexed { index, month ->
                month.jsonObject.forEach { (dayOfMonth, times) ->
                    val date = runCatching {
                        LocalDate.of(year, index + 1, dayOfMonth.toInt())
                    }.getOrNull()
                    val values = (times as? JsonArray)
                        ?.mapNotNull { it.jsonPrimitive.contentOrNullSafe() }
                    if (date != null && values != null) put(date, values)
                }
            }
        }

        return MosqueCalendar(zone = zone, days = days)
    }

    /**
     * Pulls the embedded configuration out of a mosque page.
     *
     * The page is a server-rendered document with the mosque's configuration inlined as a JSON
     * object literal, so the object is located by its assignment and decoded from that point.
     */
    private fun extractConfig(html: String): JsonObject? {
        val marker = html.indexOf(CONFIG_MARKER)
        val start = if (marker < 0) -1 else html.indexOf('{', marker)
        val end = if (start < 0) -1 else html.matchingBrace(start)

        return if (end < 0) {
            null
        } else {
            runCatching { json.parseToJsonElement(html.substring(start, end + 1)).jsonObject }
                .getOrNull()
        }
    }

    private fun kotlinx.serialization.json.JsonElement.toMosqueRef(): MosqueRef? {
        val obj = this as? JsonObject ?: return null
        val id = obj["uuid"]?.jsonPrimitive?.contentOrNullSafe() ?: return null
        val slug = obj["slug"]?.jsonPrimitive?.contentOrNullSafe() ?: return null
        val name = obj["name"]?.jsonPrimitive?.contentOrNullSafe() ?: return null

        // A mosque can switch congregation announcements off entirely, in which case whatever is
        // still sitting in the `iqama` field is stale and must not be shown as current.
        val publishesIqama = obj["iqamaEnabled"]?.jsonPrimitive?.contentOrNullSafe() != "false"
        val iqama = obj["iqama"].takeIf { publishesIqama }

        return MosqueRef(
            id = id,
            slug = slug,
            name = name,
            locality = obj["localisation"]?.jsonPrimitive?.contentOrNullSafe(),
            distanceMetres = obj["proximity"]?.jsonPrimitive?.contentOrNullSafe()?.toIntOrNull(),
            iqamaOffsets = iqama?.let { parseIqamaOffsets(it) },
            iqamaTimes = iqama?.let { parseIqamaTimes(it) },
        )
    }

    /**
     * The five Iqama fields, when the mosque publishes them as offsets like `"+20"`.
     *
     * The same field carries either offsets or wall-clock times depending on how the mosque
     * configured its display — confirmed against the live directory, where neighbouring mosques in
     * one city returned `["+25","+15",…]` and `["04:56","12:46",…]` respectively. Reading only the
     * offset form makes "the mosque's offsets" silently do nothing for the mosques that publish
     * times: every field fails to parse and the whole set is dropped.
     */
    private fun parseIqamaOffsets(
        element: kotlinx.serialization.json.JsonElement,
    ): MosqueIqamaOffsets? {
        val values = element.iqamaFields()
            ?.map { it.removePrefix("+").toIntOrNull() ?: return null }
            ?: return null

        return MosqueIqamaOffsets(
            fajr = values[0],
            dhuhr = values[1],
            asr = values[2],
            maghrib = values[3],
            isha = values[4],
        )
    }

    /** The same five fields, when the mosque publishes wall-clock times like `"12:46"`. */
    private fun parseIqamaTimes(
        element: kotlinx.serialization.json.JsonElement,
    ): MosqueIqamaTimes? {
        val values = element.iqamaFields()
            ?.map { it.takeIf(::isWallClock) ?: return null }
            ?: return null

        return MosqueIqamaTimes(
            fajr = values[0],
            dhuhr = values[1],
            asr = values[2],
            maghrib = values[3],
            isha = values[4],
        )
    }

    /**
     * The five non-blank strings of an `iqama` array, or null.
     *
     * All five or none: a partial set would leave some prayers on the mosque's schedule and others
     * on the user's, which is a schedule nobody chose.
     */
    private fun kotlinx.serialization.json.JsonElement.iqamaFields(): List<String>? {
        val values = (this as? JsonArray)?.map { it.jsonPrimitive.contentOrNullSafe() ?: return null }
        return values?.takeIf { it.size >= IQAMA_COUNT }?.take(IQAMA_COUNT)
    }

    private fun isWallClock(value: String): Boolean = WALL_CLOCK.matches(value)

    private suspend fun get(url: HttpUrl): String = httpClient.getBody(url, USER_AGENT)

    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://mawaqit.net/"
        private const val CONFIG_MARKER = "confData"
        private const val IQAMA_COUNT = 5

        /** `HH:mm`, which is how Mawaqit renders a fixed congregation time. */
        private val WALL_CLOCK = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")

        /**
         * Identifies this app to Mawaqit.
         *
         * Sending a real identifier rather than hiding behind a browser string is the courteous
         * thing to do when reading someone else's public data, and it lets them contact us if this
         * ever becomes a burden.
         */
        internal const val USER_AGENT: String = "SalahWatch/1.0 (Wear OS complication app)"
    }
}

/**
 * The index of the brace closing the object that opens at [start], or -1.
 *
 * The configuration is inlined in a larger script, so it cannot simply be read to the end of the
 * document; string contents are skipped so a brace inside a mosque name cannot end the scan early.
 */
private fun String.matchingBrace(start: Int): Int {
    var depth = 0
    var inString = false
    var escaped = false

    for (i in start until length) {
        val c = this[i]
        when {
            escaped -> escaped = false
            c == '\\' && inString -> escaped = true
            c == '"' -> inString = !inString
            inString -> Unit
            c == '{' -> depth++
            c == '}' -> {
                depth--
                if (depth == 0) return i
            }
        }
    }
    return -1
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    content.takeIf { it.isNotBlank() && it != "null" }
