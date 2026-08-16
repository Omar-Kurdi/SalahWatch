package com.salahwatch.wear.complications

import com.salahwatch.core.calculation.PhaseContent
import com.salahwatch.core.calculation.TimeKind
import com.salahwatch.core.model.Prayer
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which word appears above the number.
 *
 * The specified layouts are `Asr / 3:50`, `Iqama / 4:05` and `Pray 3:12`, so the title tracks what
 * the value *is* rather than always repeating the prayer. Getting this wrong is invisible in a
 * screenshot of a single moment — the complication looks fine, it is just labelled with the wrong
 * thing three minutes later.
 */
class ComplicationTitleTest {

    private val moment: Instant = Instant.parse("2026-08-15T11:49:00Z")

    private fun title(content: PhaseContent) =
        ComplicationTexts.titleTextFor(Prayer.ASR, content, ComplicationLabels.Fallback)

    @Test
    fun `waiting for the Adhan is titled with the prayer`() {
        assertEquals("ASR", title(PhaseContent.AbsoluteTime(moment, TimeKind.ADHAN)))
        assertEquals("ASR", title(PhaseContent.CountDown(moment, TimeKind.ADHAN)))
    }

    @Test
    fun `waiting for the congregation is titled Iqama`() {
        // Not the prayer's name: by this point the Adhan has been called and the number on screen
        // is counting to something else.
        assertEquals("Iqama", title(PhaseContent.CountDown(moment, TimeKind.IQAMA)))
    }

    @Test
    fun `the minutes after the Iqama are titled Pray`() {
        assertEquals("Pray", title(PhaseContent.CountUp(moment, TimeKind.IQAMA)))
    }
}
