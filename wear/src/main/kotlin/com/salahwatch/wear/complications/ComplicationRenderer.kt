package com.salahwatch.wear.complications

import android.app.PendingIntent
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicFloat
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import com.salahwatch.core.calculation.PrayerPhase
import java.time.ZoneId

/**
 * Assembles [ComplicationData] from a [PrayerPhase].
 *
 * The constraint that shapes everything here is the **7-character budget**:
 * `ShortTextComplicationData.MAX_TEXT_LENGTH` is 7, and it applies to the `text` and `title` fields
 * *independently*. That is why "ASR 3:50" is never emitted as a single string — it is 8 characters
 * and the watch face would truncate it. It goes out as `title="ASR"`, `text="3:50"`, which is also
 * exactly the stacked layout the specification illustrates.
 *
 * Two types are supported and no more. Every one of the five complications is a `title / value`
 * pair, which is what SHORT_TEXT is, and a gauge slot gets the same pair with an arc around it.
 * There is no LONG_TEXT rendering: nothing in the specification is a sentence, and the provider that
 * existed to fill wide slots was removed along with it.
 */
internal object ComplicationRenderer {

    /** SHORT_TEXT: what the value is as the title, the live value as the text. */
    fun shortText(
        rendered: RenderedPhase,
        zone: ZoneId,
        tapAction: PendingIntent?,
        labels: ComplicationLabels = ComplicationLabels.Fallback,
    ): ComplicationData {
        val phase = rendered.phase
        return ShortTextComplicationData.Builder(
            text = ComplicationTexts.forContent(phase.content, zone, rendered.granularity),
            contentDescription =
            ComplicationTexts.describe(phase.prayer, phase.content, phase.source, labels),
        )
            .setTitle(ComplicationTexts.titleFor(phase.prayer, phase.content, labels))
            .setTapAction(tapAction)
            .build()
    }

    /**
     * RANGED_VALUE: the same pair, with an arc that fills across the phase's segment.
     *
     * The arc is a [DynamicFloat] the platform evaluates itself at 1 Hz, not a static float. A plain
     * value is fixed until the provider is asked again, and the minimum update period is 300
     * seconds, so a static arc would sit motionless and then jump every five minutes. This is also
     * the reason the watch app requires API 33 — the dynamic-value constructors begin there.
     *
     * It is measured from [PrayerPhase.arcFrom] to [PrayerPhase.arcTo] rather than from the entry's
     * own bounds, so a countdown that has been split to switch into seconds does not empty the arc
     * and start it again at the switch. One formula covers all three kinds of content, because in
     * every case the arc is asking the same question: how much of this segment has gone.
     */
    fun rangedValue(
        rendered: RenderedPhase,
        zone: ZoneId,
        tapAction: PendingIntent?,
        labels: ComplicationLabels = ComplicationLabels.Fallback,
    ): ComplicationData {
        val phase = rendered.phase
        val span = (phase.arcTo.epochSecond - phase.arcFrom.epochSecond).coerceAtLeast(1)

        val remaining = DynamicInstant.platformTimeWithSecondsPrecision()
            .durationUntil(DynamicInstant.withSecondsPrecision(phase.arcTo))
            .toIntSeconds()
        val elapsed = DynamicFloat.constant(span.toFloat()).minus(remaining)

        return RangedValueComplicationData.Builder(
            dynamicValue = elapsed,
            // Used only if the platform cannot evaluate the expression, so the arc degrades to a
            // correct-at-publish-time value rather than to nothing.
            fallbackValue = 0f,
            min = 0f,
            max = span.toFloat(),
            contentDescription =
            ComplicationTexts.describe(phase.prayer, phase.content, phase.source, labels),
        )
            .setText(ComplicationTexts.forContent(phase.content, zone, rendered.granularity))
            .setTitle(ComplicationTexts.titleFor(phase.prayer, phase.content, labels))
            .setTapAction(tapAction)
            .build()
    }

    /** Whichever of the two supported types was asked for, or null for anything else. */
    fun forType(
        type: ComplicationType,
        rendered: RenderedPhase,
        zone: ZoneId,
        tapAction: PendingIntent?,
        labels: ComplicationLabels = ComplicationLabels.Fallback,
    ): ComplicationData? = when (type) {
        ComplicationType.SHORT_TEXT -> shortText(rendered, zone, tapAction, labels)
        ComplicationType.RANGED_VALUE -> rangedValue(rendered, zone, tapAction, labels)
        else -> null
    }

    /** Placeholder shown when no schedule is available — never a zeroed countdown. */
    fun noData(type: ComplicationType, tapAction: PendingIntent?): ComplicationData? {
        val message = ComplicationTexts.plain(NO_DATA_TEXT)
        val description = ComplicationTexts.plain(NO_DATA_DESCRIPTION)

        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(message, description)
                    .setTapAction(tapAction).build()

            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(0f, 0f, 1f, description)
                    .setText(message).setTapAction(tapAction).build()

            else -> null
        }
    }

    private const val NO_DATA_TEXT = "--:--"
    private const val NO_DATA_DESCRIPTION =
        "Prayer times unavailable. Open the app to set a location."
}
