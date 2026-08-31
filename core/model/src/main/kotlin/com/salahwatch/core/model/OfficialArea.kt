package com.salahwatch.core.model

import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.serialization.Serializable

/**
 * An area whose official timetable is bundled with the app.
 *
 * These are transcriptions of a published national table, not a calculation and not a mosque's own
 * sheet, so they reproduce the authority's times exactly for the days they cover. The trade is
 * coverage: only the areas listed here have one, and each bundle ends on the last day the authority
 * had published when it was captured (see `OfficialTimetable`). Outside either boundary the app
 * falls back to calculation rather than guessing.
 *
 * Coordinates are the area centre and exist only to preselect the right table for a user who has
 * not chosen one. They are not used to compute anything.
 */
@Suppress("MagicNumber")
@Serializable
public enum class OfficialArea(
    /** Matches the bundled asset filename, and is the stable identifier written to preferences. */
    public val slug: String,
    public val latitude: Double,
    public val longitude: Double,
) {
    AL_AIN("al-ain", 24.1917, 55.7606),
    DUBAI("dubai", 25.2048, 55.2708),
    ;

    /** Every bundled area is in the UAE, which has one zone and no daylight saving. */
    public val zone: ZoneId get() = ZoneId.of("Asia/Dubai")

    public companion object {
        /**
         * How far from an area's centre its table is still offered automatically.
         *
         * Wide enough to cover a metropolitan area and its outskirts, narrow enough that a user in
         * another emirate is not silently given the wrong city's timetable — the failure that
         * would be hardest to notice, because the times would look entirely plausible.
         */
        public const val MAX_AUTO_DISTANCE_KM: Double = 60.0

        /** The bundled area covering these coordinates, or null when none is close enough. */
        public fun nearestTo(latitude: Double, longitude: Double): OfficialArea? = entries
            .map { it to it.distanceKmTo(latitude, longitude) }
            .filter { (_, km) -> km <= MAX_AUTO_DISTANCE_KM }
            .minByOrNull { (_, km) -> km }
            ?.first
    }
}

/** Great-circle distance in kilometres, for choosing between a handful of fixed points. */
private fun OfficialArea.distanceKmTo(latitude: Double, longitude: Double): Double {
    val dLat = Math.toRadians(latitude - this.latitude)
    val dLon = Math.toRadians(longitude - this.longitude)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(this.latitude)) * cos(Math.toRadians(latitude)) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
}

private const val EARTH_RADIUS_KM = 6371.0
