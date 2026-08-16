package com.salahwatch.core.calculation

import com.salahwatch.core.model.CalculationSettings
import com.salahwatch.core.model.IqamaSettings
import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.PrayerLocation
import com.salahwatch.core.model.School
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Measures how far the offline calculator drifts from AlAdhan.
 *
 * The project ships locally calculated times as a marked estimate when nothing is cached. That
 * promise is only honest if the size of the estimate is known, so this test pins it against real API
 * responses captured in `aladhan-golden.json` — 5 locations x 2 methods x 2 schools x 4 dates,
 * spanning solstices, equinoxes and both DST changeovers.
 *
 * The fixture is committed rather than fetched: a test that hits the network is a test that fails
 * on a train. Regenerate it with the script documented in RESEARCH.md when methods change.
 */
class AlAdhanGoldenTest {

    @Serializable
    private data class Golden(
        val location: String,
        val latitude: Double,
        val longitude: Double,
        val method: Int,
        val school: Int,
        val date: String,
        val timezone: String,
        val timings: Map<String, String>,
    )

    private val calculator = LocalPrayerCalculator()

    private val goldens: List<Golden> by lazy {
        val stream = checkNotNull(javaClass.getResourceAsStream("/aladhan-golden.json")) {
            "aladhan-golden.json is missing from test resources"
        }
        json.decodeFromString<List<Golden>>(stream.reader().readText())
    }

    /** Signed difference in minutes, local minus AlAdhan, for every prayer in every sample. */
    private fun deviations(): List<Triple<Golden, Prayer, Long>> = goldens.flatMap { golden ->
        val schedule = calculator.calculate(
            location = PrayerLocation(
                latitude = golden.latitude,
                longitude = golden.longitude,
                zone = ZoneId.of(golden.timezone),
            ),
            date = LocalDate.parse(golden.date),
            calculation = CalculationSettings(
                methodId = golden.method,
                school = if (golden.school == 1) School.HANAFI else School.SHAFI,
            ),
            iqama = IqamaSettings(),
        )

        golden.timings.mapNotNull { (name, iso) ->
            val prayer = Prayer.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: return@mapNotNull null
            val expected = OffsetDateTime.parse(iso).toInstant()
            val actual = schedule[prayer]?.adhan ?: return@mapNotNull null
            Triple(golden, prayer, Duration.between(expected, actual).toMinutes())
        }
    }

    @Test
    fun `fixture covers the intended matrix`() {
        assertTrue(goldens.size >= EXPECTED_SAMPLES, "Expected at least $EXPECTED_SAMPLES samples")
        assertTrue(goldens.map { it.location }.distinct().size >= EXPECTED_LOCATIONS)
    }

    @Test
    fun `offline calculation stays within the documented tolerance of AlAdhan`() {
        val offenders = deviations().filter { abs(it.third) > TOLERANCE_MINUTES }

        assertTrue(
            offenders.isEmpty(),
            buildString {
                appendLine("${offenders.size} prayer times exceed ±$TOLERANCE_MINUTES min:")
                offenders.take(MAX_REPORTED).forEach { (golden, prayer, delta) ->
                    appendLine(
                        "  ${golden.location} ${golden.date} method=${golden.method} " +
                            "school=${golden.school} ${prayer.shortName}: ${delta}m",
                    )
                }
            },
        )
    }

    /**
     * Prints the measured deviation so the "estimated" caveat shown in the UI is backed by a number
     * someone has actually looked at, not an assumption. Always passes; it exists for its output.
     */
    @Test
    fun `report deviation distribution`() {
        val all = deviations()
        val byPrayer = all.groupBy { it.second }

        println("AlAdhan vs local calculation — ${all.size} comparisons")
        for (prayer in Prayer.entries) {
            val deltas = byPrayer[prayer]?.map { it.third } ?: continue
            val worst = deltas.maxByOrNull { abs(it) } ?: 0
            val exact = deltas.count { it == 0L }
            println(
                "  ${prayer.shortName.padEnd(8)} " +
                    "exact ${exact}/${deltas.size}  " +
                    "range ${deltas.min()}..${deltas.max()}  worst |${abs(worst)}|",
            )
        }

        // Asr is the least exact prayer, and the error is school-dependent: the Shafi shadow
        // ratio (1x) puts Asr where the sun's altitude is changing more slowly than at the Hanafi
        // time (2x), so the same small difference in computed altitude converts into a larger
        // difference in minutes. Printed per school so the claim stays checkable.
        println("  Asr by school:")
        byPrayer[Prayer.ASR].orEmpty()
            .groupBy { it.first.school }
            .toSortedMap()
            .forEach { (school, samples) ->
                val deltas = samples.map { it.third }
                val label = if (school == 1) "Hanafi" else "Shafi "
                println(
                    "    $label (school=$school) " +
                        "exact ${deltas.count { it == 0L }}/${deltas.size}  " +
                        "range ${deltas.min()}..${deltas.max()}  " +
                        "mean ${"%+.2f".format(deltas.average())}  " +
                        "meanAbs ${"%.2f".format(deltas.map { abs(it) }.average())}",
                )
            }

        println("  Asr breakdown by school and location:")
        byPrayer[Prayer.ASR].orEmpty()
            .groupBy { it.first.school to it.first.location }
            .toSortedMap(compareBy({ it.first }, { it.second }))
            .forEach { (key, samples) ->
                val (school, location) = key
                val deltas = samples.map { it.third }
                println(
                    "    school=$school ${location.padEnd(10)} " +
                        "${deltas.sorted()}  mean=${"%.2f".format(deltas.average())}",
                )
            }

        assertTrue(all.isNotEmpty())
    }

    @Test
    fun `calculated instants are absolute and independent of the default timezone`() {
        // A device travelling does not change when the sun crosses the horizon where it started.
        val location = PrayerLocation(24.2075, 55.7447, ZoneId.of("Asia/Dubai"))
        val date = LocalDate.of(2026, 8, 14)
        val settings = CalculationSettings()

        fun computeUnder(zone: String): List<Instant> {
            val original = java.util.TimeZone.getDefault()
            return try {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone))
                calculator.calculate(location, date, settings, IqamaSettings()).times.map { it.adhan }
            } finally {
                java.util.TimeZone.setDefault(original)
            }
        }

        assertTrue(
            computeUnder("Pacific/Auckland") == computeUnder("America/Los_Angeles"),
            "Prayer instants must not depend on the device's own timezone",
        )
    }

    @Test
    fun `a range spanning a DST boundary keeps stable local wall-clock times`() {
        val london = PrayerLocation(51.5074, -0.1278, ZoneId.of("Europe/London"))
        val schedules = calculator.calculateRange(
            location = london,
            from = LocalDate.of(2026, 10, 24),
            days = 3,
            calculation = CalculationSettings(),
            iqama = IqamaSettings(),
        )

        val dhuhrHours = schedules.map {
            val dhuhr = checkNotNull(it[Prayer.DHUHR]).adhan
            dhuhr.atZone(london.zone).hour
        }

        // Dhuhr sits near local noon on every one of the three days. Adding 24h per day instead of
        // recomputing would push the post-transition days an hour out.
        assertTrue(
            dhuhrHours.all { it in NOON_WINDOW },
            "Dhuhr drifted across the DST boundary: $dhuhrHours",
        )
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        /**
         * Measured, not chosen. With the method table and the ANGLE_BASED high-latitude rule in
         * place, the residual disagreement is: Dhuhr and Maghrib exact on all 80 samples; Fajr,
         * Sunrise and Isha within 1 minute; Asr within 3, spread symmetrically about zero rather
         * than biased, so there is no systematic correction left to make. 3 minutes is therefore the
         * honest bound on "estimated" times, and this constant is what holds that claim to account.
         */
        const val TOLERANCE_MINUTES = 3L
        const val EXPECTED_SAMPLES = 80
        const val EXPECTED_LOCATIONS = 5
        const val MAX_REPORTED = 15
        val NOON_WINDOW = 11..13
    }
}
