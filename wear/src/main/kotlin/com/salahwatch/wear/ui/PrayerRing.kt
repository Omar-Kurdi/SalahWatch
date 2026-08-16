package com.salahwatch.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.salahwatch.core.calculation.PrayerOccurrence
import com.salahwatch.core.model.Prayer
import java.time.Instant
import kotlin.math.cos
import kotlin.math.sin

/**
 * The day drawn around the bezel: prayers as marks on a ring, and a brass dot for now.
 *
 * A round watch already has a circle to work with, so putting the day on it costs no screen space —
 * the numbers stay in the middle where they are read, and the shape of the day sits in the margin
 * that would otherwise be empty. The ring answers "how far through the day" and "is the next prayer
 * close" without occupying a single line of text.
 *
 * Twelve o'clock is midnight and the sweep is a full 24 hours, so the marks sit where the sun
 * actually is. That makes the ring read the same way every day rather than rescaling itself.
 */
@Composable
internal fun PrayerRing(
    prayers: List<PrayerOccurrence>,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxSize()) {
        val zone = prayers.firstOrNull()?.zone ?: return@Canvas
        val startOfDay = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

        fun angleOf(instant: Instant): Float {
            val secondsIn = (instant.epochSecond - startOfDay.epochSecond).toFloat()
            return (secondsIn / SECONDS_PER_DAY) * FULL_TURN - QUARTER_TURN
        }

        drawTrack()
        for (prayer in prayers) {
            drawPrayerMark(angleOf(prayer.adhan), colourFor(prayer.prayer))
        }
        drawNow(angleOf(now))
    }
}

/** The unlit part of the day. Dim enough to read as a groove rather than a border. */
private fun DrawScope.drawTrack() {
    drawCircle(
        color = WearColors.Track,
        radius = ringRadius(),
        style = Stroke(width = TRACK_WIDTH),
    )
}

/** One prayer, in the colour of its sky. */
private fun DrawScope.drawPrayerMark(angleDegrees: Float, colour: Color) {
    val radians = Math.toRadians(angleDegrees.toDouble())
    val radius = ringRadius()
    val cx = size.width / 2 + cos(radians).toFloat() * radius
    val cy = size.height / 2 + sin(radians).toFloat() * radius

    drawCircle(color = colour, radius = MARK_RADIUS, center = Offset(cx, cy))
}

/**
 * Now.
 *
 * The only brass element and the only thing that moves, drawn as a filled dot with a dark rim so it
 * stays visible where it overlaps a prayer mark.
 */
private fun DrawScope.drawNow(angleDegrees: Float) {
    val radians = Math.toRadians(angleDegrees.toDouble())
    val radius = ringRadius()
    val cx = size.width / 2 + cos(radians).toFloat() * radius
    val cy = size.height / 2 + sin(radians).toFloat() * radius
    val centre = Offset(cx, cy)

    drawCircle(color = WearColors.Night, radius = NOW_RADIUS + NOW_RIM, center = centre)
    drawCircle(color = WearColors.Brass, radius = NOW_RADIUS, center = centre)
}

private fun DrawScope.ringRadius(): Float =
    minOf(size.width, size.height) / 2 - RING_INSET

private fun colourFor(prayer: Prayer): Color = when (prayer) {
    Prayer.FAJR -> WearColors.Fajr
    Prayer.SUNRISE -> WearColors.Sunrise
    Prayer.DHUHR -> WearColors.Zenith
    Prayer.ASR -> WearColors.Afternoon
    Prayer.MAGHRIB -> WearColors.Maghrib
    Prayer.ISHA -> WearColors.Isha
}

private const val SECONDS_PER_DAY = 86_400f
private const val FULL_TURN = 360f
private const val QUARTER_TURN = 90f
private const val TRACK_WIDTH = 2f
private const val MARK_RADIUS = 3.5f
private const val NOW_RADIUS = 5f
private const val NOW_RIM = 2.5f
private const val RING_INSET = 6f
