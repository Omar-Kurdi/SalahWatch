package com.salahwatch.app.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The sizing rules, which are otherwise invisible without a launcher to resize the widget in.
 *
 * The cases that matter are the two ends: a widget shrunk to two cells must still show all six
 * prayers rather than losing Isha off the bottom, and a widget whose size the launcher has not
 * reported yet must not be drawn as though it were tiny.
 */
class WidgetSizingTest {

    /**
     * Roughly what a launcher reports, at the ~70dp cell a five-column phone grid gives.
     *
     * The number matters: guess it too small and the thresholds get tuned against a widget size
     * that never occurs, which is how a three-cell widget ends up rendering as though it were two.
     */
    private fun cells(wide: Int, tall: Int) = WidgetSize(widthDp = wide * 70, heightDp = tall * 70)

    @Test
    fun `the conservative box is the smaller of each axis, not min of both keys`() {
        // Portrait is MIN_WIDTH x MAX_HEIGHT; landscape is MAX_WIDTH x MIN_HEIGHT.
        val size = WidgetSize.of(minWidth = 250, maxWidth = 300, minHeight = 100, maxHeight = 180)

        assertEquals(WidgetSize(widthDp = 250, heightDp = 100), size)
    }

    @Test
    fun `an unreported size draws at full size rather than compact`() {
        // The options bundle is empty on first placement, so both values read zero. Treating that
        // as "tiny" would leave a four-cell widget rendering in 10sp until it was resized.
        assertTrue(WidgetSize(0, 0).isUnknown)
        assertEquals(WidgetDensity.FULL, gridLayoutFor(WidgetSize(0, 0)).density)
        assertEquals(WidgetDensity.FULL, gridLayoutFor(WidgetSize(250, 0)).density)
        assertEquals(FULL_STRIP_MERIDIEM, stripLayoutFor(WidgetSize(0, 0)).showMeridiem)
    }

    @Test
    fun `a real size is not treated as unknown`() {
        assertFalse(cells(4, 4).isUnknown)
    }

    @Test
    fun `four cells square gets everything`() {
        val layout = gridLayoutFor(cells(4, 4))

        assertEquals(WidgetDensity.FULL, layout.density)
        assertTrue(layout.showHeader)
        assertTrue(layout.showIqama)
        assertTrue(layout.showNotice)
    }

    @Test
    fun `three cells square is enough for the whole thing`() {
        // The size the widget is actually meant to be used at. If this ever regresses to MEDIUM the
        // default placement has silently become cramped.
        val layout = gridLayoutFor(cells(3, 3))

        assertEquals(WidgetDensity.FULL, layout.density)
        assertTrue(layout.showHeader)
        assertTrue(layout.showIqama)
    }

    @Test
    fun `an in-between height drops the chrome but keeps both time columns`() {
        val layout = gridLayoutFor(WidgetSize(widthDp = 210, heightDp = 160))

        assertEquals(WidgetDensity.MEDIUM, layout.density)
        assertFalse(layout.showHeader, "The date line is the first thing worth losing")
        assertTrue(layout.showIqama)
    }

    @Test
    fun `two cells shrinks the type rather than dropping a prayer`() {
        // The reported complaint: at two cells the old fixed layout ran Isha off the bottom.
        val layout = gridLayoutFor(cells(2, 2))

        assertEquals(WidgetDensity.COMPACT, layout.density)
        assertFalse(layout.showHeader)
        assertEquals(0, layout.rowPaddingDp)
        assertTrue(layout.rowTextSp < gridLayoutFor(cells(4, 4)).rowTextSp)
        assertFalse(layout.showIqama, "Two cells is too narrow for a second time column")
    }

    @Test
    fun `type and padding never grow as the widget shrinks`() {
        val sizes = listOf(cells(4, 5), cells(4, 4), cells(3, 3), cells(2, 2))

        sizes.map(::gridLayoutFor).zipWithNext { bigger, smaller ->
            assertTrue(
                smaller.rowTextSp <= bigger.rowTextSp,
                "Row text grew from ${bigger.rowTextSp} to ${smaller.rowTextSp} on a smaller widget",
            )
            assertTrue(smaller.countdownTextSp <= bigger.countdownTextSp)
            assertTrue(smaller.rowPaddingDp <= bigger.rowPaddingDp)
        }
    }

    @Test
    fun `a narrow widget drops the Iqama column, whatever its height`() {
        assertFalse(gridLayoutFor(WidgetSize(widthDp = 140, heightDp = 280)).showIqama)
        assertFalse(gridLayoutFor(WidgetSize(widthDp = 140, heightDp = 110)).showIqama)
    }

    @Test
    fun `the strip keeps AM PM until it genuinely cannot fit`() {
        assertTrue(stripLayoutFor(WidgetSize(widthDp = 260, heightDp = 60)).showMeridiem)
        assertTrue(stripLayoutFor(WidgetSize(widthDp = 200, heightDp = 60)).showMeridiem)
        assertFalse(
            stripLayoutFor(WidgetSize(widthDp = 140, heightDp = 60)).showMeridiem,
            "Below about three cells the meridiem is what has to give",
        )
    }

    @Test
    fun `strip type never grows as it narrows`() {
        val layouts = listOf(300, 250, 200, 140)
            .map { stripLayoutFor(WidgetSize(widthDp = it, heightDp = 60)) }

        layouts.zipWithNext { bigger, smaller ->
            assertTrue(smaller.timeTextSp <= bigger.timeTextSp)
            assertTrue(smaller.iconSizeDp <= bigger.iconSizeDp)
        }
    }

    private companion object {
        const val FULL_STRIP_MERIDIEM = true
    }
}
