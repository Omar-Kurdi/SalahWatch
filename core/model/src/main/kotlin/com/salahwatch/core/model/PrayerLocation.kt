package com.salahwatch.core.model

import java.time.ZoneId
import kotlinx.serialization.Serializable

/**
 * A resolved place to calculate prayer times for.
 *
 * Coordinates are the canonical representation, never a city name. The AlAdhan city endpoints
 * require geocoding via 7x.ax, which shuts down on 2026-09-01, and they already return masked
 * coordinates without a key — so `timingsByCity` is unusable regardless. City and country are
 * carried only as a human-readable [label]; nothing calculates from them. See RESEARCH.md §2.1(B).
 */
@Serializable
public data class PrayerLocation(
    val latitude: Double,
    val longitude: Double,
    @Serializable(with = ZoneIdSerializer::class) val zone: ZoneId,
    val label: String? = null,
) {
    init {
        require(latitude in MIN_LATITUDE..MAX_LATITUDE) { "Latitude $latitude out of range" }
        require(longitude in MIN_LONGITUDE..MAX_LONGITUDE) { "Longitude $longitude out of range" }
    }

    /**
     * True where the sun may not reach the twilight angles used by the calculation methods, so
     * Fajr and Isha fall back to a high-latitude rule and become approximations.
     */
    public val isHighLatitude: Boolean get() = kotlin.math.abs(latitude) >= HIGH_LATITUDE_THRESHOLD

    /**
     * Whether [other] is far enough away to invalidate a cached schedule.
     *
     * Uses a plain degree-box rather than a great-circle distance: the threshold only needs to
     * answer "did the user actually move somewhere else", and a box is exact enough for that while
     * being cheap and dependency-free. Roughly 5.5 km at the equator, shrinking with latitude, which
     * is well inside the distance that shifts any prayer time by a minute.
     */
    public fun isSamePlaceAs(other: PrayerLocation): Boolean =
        kotlin.math.abs(latitude - other.latitude) < COORDINATE_EPSILON &&
            kotlin.math.abs(longitude - other.longitude) < COORDINATE_EPSILON &&
            zone == other.zone

    public companion object {
        private const val MIN_LATITUDE = -90.0
        private const val MAX_LATITUDE = 90.0
        private const val MIN_LONGITUDE = -180.0
        private const val MAX_LONGITUDE = 180.0
        private const val HIGH_LATITUDE_THRESHOLD = 48.0
        private const val COORDINATE_EPSILON = 0.05
    }
}

/** How the app decides which [PrayerLocation] to use. */
@Serializable
public sealed interface LocationMode {
    /**
     * Track the device's location.
     *
     * [last] caches the most recent fix so the app keeps working when a fix is unavailable — the
     * watch is off-body, the phone is out of range, or permission was revoked. Foreground, coarse
     * accuracy only; no background location permission is ever requested.
     */
    @Serializable
    public data class Automatic(val last: PrayerLocation? = null) : LocationMode

    /**
     * A place the user entered by hand.
     *
     * SPEC.md calls this out as important for stability: a fixed location means prayer times never
     * shift because of a stray GPS fix.
     */
    @Serializable
    public data class Manual(val location: PrayerLocation) : LocationMode
}

/** The location currently in effect, or null if Automatic mode has never obtained a fix. */
public val LocationMode.effectiveLocation: PrayerLocation?
    get() = when (this) {
        is LocationMode.Automatic -> last
        is LocationMode.Manual -> location
    }
