package com.salahwatch.wear.ui

import androidx.compose.ui.graphics.Color

/**
 * The same sky palette as the phone, on a true-black base.
 *
 * `#000000` rather than the phone's deep blue: on an AMOLED watch face a true black pixel is an
 * unlit pixel, so the background costs no power and the ring appears to float on the glass.
 */
internal object WearColors {
    val Night = Color(0xFF000000)
    val Surface = Color(0xFF10151F)

    val Fajr = Color(0xFF4A5A99)
    val Sunrise = Color(0xFFD9884A)
    val Zenith = Color(0xFF7FA6D9)
    val Afternoon = Color(0xFFC9922E)
    val Maghrib = Color(0xFFB4573A)
    val Isha = Color(0xFF39427A)

    val Brass = Color(0xFFC9A227)
    val Track = Color(0xFF1E2536)

    val Ink = Color(0xFFF0F2F8)
    val InkMuted = Color(0xFF98A1B8)
    val InkFaint = Color(0xFF5F6880)
}
