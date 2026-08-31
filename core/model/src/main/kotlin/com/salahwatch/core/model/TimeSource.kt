package com.salahwatch.core.model

import kotlinx.serialization.Serializable

/** Where prayer times come from. */
@Serializable
public enum class TimeSource {
    /**
     * Calculated astronomically for your coordinates, via AlAdhan.
     *
     * Works anywhere and needs nothing but a location, but a calculation can only ever approximate
     * a local timetable: national authorities apply their own conventions that no set of angles
     * reproduces. Sunrise in particular is method-independent, so no method choice can move it.
     */
    CALCULATED,

    /**
     * The published times of a nearby mosque, via Mawaqit.
     *
     * These are the times a congregation actually prays by, which tracks a national timetable far
     * more closely than a calculation can. It is not the same thing as the authority's table: a
     * mosque publishes its own, and measured over a month against Awqaf's Al Ain table the nearest
     * mosque was exact on 27/31 days for Fajr and 23/31 for Maghrib, never out by more than a
     * minute. Neighbouring mosques can be on an entirely different table, so proximity alone does
     * not imply the local authority's times.
     */
    NEARBY_MOSQUE,
}

/**
 * A mosque the user has chosen to follow.
 *
 * [slug] is what fetches the calendar; [id] is Mawaqit's stable uuid. Both are kept because the slug
 * is derived from the mosque's name and address and can change if either is edited.
 */
@Serializable
public data class MosqueRef(
    val id: String,
    val slug: String,
    val name: String,
    val locality: String? = null,
    /** Metres from the location that found it, when known. */
    val distanceMetres: Int? = null,
    /** The mosque's own Iqama offsets, when it publishes them that way. */
    val iqamaOffsets: MosqueIqamaOffsets? = null,
    /**
     * The mosque's own Iqama times, when it publishes wall-clock times instead of offsets.
     *
     * Mawaqit lets a mosque choose either form and returns whichever it uses in the same field, so
     * both have to be carried. Fixed times take precedence when a mosque somehow publishes both:
     * they are the more specific statement of when the congregation actually starts.
     */
    val iqamaTimes: MosqueIqamaTimes? = null,
) {
    /** True when the mosque publishes congregation times in either form. */
    public val hasIqama: Boolean get() = iqamaTimes != null || iqamaOffsets != null
}

/**
 * Minutes after each Adhan that a mosque begins its congregation.
 *
 * Sunrise is absent because it has no congregation. Stored alongside the mosque rather than in
 * [IqamaSettings] so that switching mosque brings its offsets with it, and switching back to fixed
 * offsets leaves the user's own values untouched.
 */
@Serializable
public data class MosqueIqamaOffsets(
    val fajr: Int,
    val dhuhr: Int,
    val asr: Int,
    val maghrib: Int,
    val isha: Int,
) {
    public fun offsetFor(prayer: Prayer): Int? = when (prayer) {
        Prayer.FAJR -> fajr
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
        Prayer.SUNRISE -> null
    }
}

/**
 * Wall-clock Iqama times, for the mosques that publish times rather than offsets.
 *
 * Held as `HH:mm` strings so this module stays free of `java.time` and can be shared with the
 * watch unchanged; [com.salahwatch.core.calculation.IqamaCalculator] is what turns them
 * into instants, because only it knows the day and the zone they belong to.
 *
 * A mosque that publishes times rather than offsets is making a different statement: the
 * congregation starts at 04:56 whatever the Adhan does, rather than twenty minutes after it. That
 * distinction is why this is a separate type instead of offsets computed at parse time — computing
 * them would silently re-derive the wrong thing on every other day of the year.
 */
@Serializable
public data class MosqueIqamaTimes(
    val fajr: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
) {
    /** The published `HH:mm` for [prayer], or null where there is no congregation. */
    public fun timeFor(prayer: Prayer): String? = when (prayer) {
        Prayer.FAJR -> fajr
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
        Prayer.SUNRISE -> null
    }
}
