package com.salahwatch.app.ui

import androidx.compose.ui.graphics.Color

/**
 * The palette is derived from the sky, because that is what prayer times measure.
 *
 * Each of [Fajr] through [Isha] is the colour of the sky at the moment that prayer falls, and those
 * same values drive the gradient in the day arc — so the arc is not decoration, it is the data drawn
 * as light. [Brass] is the one interface accent: the material Islamic astronomical instruments were
 * made from, which is closer to this subject than a generic gold would be.
 */
internal object PrayerColors {
    /** True night. Deep enough for AMOLED, blue rather than grey so it reads as sky, not as chrome. */
    val Night = Color(0xFF05070F)
    val Surface = Color(0xFF0D1220)
    val SurfaceRaised = Color(0xFF151C2E)

    /** The sky, in order through the day. */
    val PreDawn = Color(0xFF141B33)
    val Fajr = Color(0xFF2E3A6B)
    val Dawn = Color(0xFF6B5A93)
    val Sunrise = Color(0xFFD9884A)
    val Zenith = Color(0xFF7FA6D9)
    val Afternoon = Color(0xFFC9922E)
    val Maghrib = Color(0xFFB4573A)
    val Isha = Color(0xFF1E2547)

    /** The single interface accent. */
    val Brass = Color(0xFFC9A227)
    val BrassDim = Color(0xFF7A6318)

    val Ink = Color(0xFFF0F2F8)
    val InkMuted = Color(0xFF9AA3BC)
    val InkFaint = Color(0xFF5C6480)
    val Line = Color(0xFF1F2739)
    val Warning = Color(0xFFD98F5F)

    /**
     * The day-lit counterpart.
     *
     * Not an inversion: the same sky palette read at noon rather than at night. Brass darkens to
     * stay legible on a pale ground, and the surfaces are warm rather than clinical white so the
     * sunrise and maghrib colours in the day arc still sit in the same family.
     */
    val DayBackground = Color(0xFFF7F4EC)
    val DaySurface = Color(0xFFFFFFFF)
    val DaySurfaceRaised = Color(0xFFEFEADD)
    val DayBrass = Color(0xFF8A6D12)
    val DayInk = Color(0xFF15181F)
    val DayInkMuted = Color(0xFF5A6172)
    val DayInkFaint = Color(0xFF8D93A3)
    val DayLine = Color(0xFFDED8C9)
}
