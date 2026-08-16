package com.salahwatch.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.salahwatch.core.calculation.PrayerOccurrence
import com.salahwatch.core.model.Prayer
import java.time.Instant
import kotlin.math.roundToInt

/**
 * The day drawn as light: a band of sky from Fajr to Isha, with each prayer marked where it falls
 * and a brass line for the current moment.
 *
 * This exists because a prayer schedule is not really a list — it is a set of positions of the sun,
 * and a flat list throws that away. Reading the band tells you something a list cannot: how much of
 * the day is behind you, whether the next prayer is close, and where the long gaps are.
 *
 * The gradient stops are placed at the *actual* prayer times rather than at even intervals, so the
 * band compresses and stretches with the real shape of the day and changes across the year.
 */
@Composable
internal fun DayArc(
    prayers: List<PrayerOccurrence>,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    val ordered = prayers.filter { it.prayer != Prayer.SUNRISE || true }.sortedBy { it.adhan }
    if (ordered.size < 2) return

    val start = ordered.first().adhan
    val end = ordered.last().adhan
    val span = (end.epochSecond - start.epochSecond).coerceAtLeast(1)

    fun fractionOf(instant: Instant): Float =
        ((instant.epochSecond - start.epochSecond).toFloat() / span).coerceIn(0f, 1f)

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(BAND_HEIGHT.dp),
        ) {
            drawSkyBand(ordered.map { fractionOf(it.adhan) to it.prayer })
            drawPrayerTicks(ordered.map { fractionOf(it.adhan) })
            if (now >= start && now <= end) {
                drawNowMarker(fractionOf(now))
            }
        }
        Spacer(Modifier.height(6.dp))
        ArcLabels(ordered, ::fractionOf)
    }
}

/**
 * The sky itself.
 *
 * Colours are interpolated between the prayer positions, so dawn really does sit between Fajr and
 * sunrise and the band darkens into Isha at the right moment for today's date.
 */
private fun DrawScope.drawSkyBand(stops: List<Pair<Float, Prayer>>) {
    val colours = stops.map { (fraction, prayer) ->
        fraction to when (prayer) {
            Prayer.FAJR -> PrayerColors.Fajr
            Prayer.SUNRISE -> PrayerColors.Sunrise
            Prayer.DHUHR -> PrayerColors.Zenith
            Prayer.ASR -> PrayerColors.Afternoon
            Prayer.MAGHRIB -> PrayerColors.Maghrib
            Prayer.ISHA -> PrayerColors.Isha
        }
    }

    // Anchor the ends in night so the band reads as a full day rather than a cropped slice.
    val gradient = buildList {
        add(0f to PrayerColors.PreDawn)
        addAll(colours)
        add(1f to PrayerColors.Isha)
    }.sortedBy { it.first }

    drawRect(
        brush = Brush.horizontalGradient(
            colorStops = gradient.map { it.first to it.second }.toTypedArray(),
        ),
        size = Size(size.width, size.height),
    )
}

/** A hairline at each prayer. Quiet: the band carries the meaning, these only locate it. */
private fun DrawScope.drawPrayerTicks(fractions: List<Float>) {
    for (fraction in fractions) {
        val x = size.width * fraction
        drawLine(
            color = PrayerColors.Night.copy(alpha = TICK_ALPHA),
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = TICK_WIDTH,
        )
    }
}

/**
 * Now.
 *
 * The one brass element in the band, and the only thing that moves. Spending the accent here is
 * deliberate: "where am I in the day" is the question the band exists to answer.
 */
private fun DrawScope.drawNowMarker(fraction: Float) {
    val x = size.width * fraction
    drawLine(
        color = PrayerColors.Brass,
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = NOW_WIDTH,
    )
    drawCircle(
        color = PrayerColors.Brass,
        radius = NOW_DOT_RADIUS,
        center = Offset(x, size.height),
        style = Stroke(width = NOW_WIDTH),
    )
}

/**
 * Labels beneath the band.
 *
 * Only the endpoints and noon are labelled. Naming all six would crowd the strip at phone widths and
 * duplicate the schedule directly below it — the band's job is shape, not lookup.
 *
 * These take their colour from the palette because they sit on the page. Everything drawn *on* the
 * band above — the sky colours, the ticks, the brass now-marker — deliberately does not: the band is
 * the same picture of the sky in either theme, so its colours are properties of the data rather than
 * of the interface.
 */
@Composable
private fun ArcLabels(prayers: List<PrayerOccurrence>, fractionOf: (Instant) -> Float) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
        val first = prayers.first()
        val last = prayers.last()
        androidx.compose.material3.Text(
            text = first.prayer.shortName.lowercase().replaceFirstChar(Char::uppercase),
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = LocalPalette.current.inkFaint,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.Text(
            text = androidx.compose.ui.res.stringResource(
                com.salahwatch.app.R.string.day_progress,
                (fractionOf(java.time.Instant.now()) * PERCENT).roundToInt(),
            ),
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = LocalPalette.current.inkFaint,
        )
        androidx.compose.material3.Text(
            text = last.prayer.shortName.lowercase().replaceFirstChar(Char::uppercase),
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = LocalPalette.current.inkFaint,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

private const val BAND_HEIGHT = 44
private const val TICK_ALPHA = 0.55f
private const val TICK_WIDTH = 1.5f
private const val NOW_WIDTH = 2.5f
private const val NOW_DOT_RADIUS = 4f
private const val PERCENT = 100
