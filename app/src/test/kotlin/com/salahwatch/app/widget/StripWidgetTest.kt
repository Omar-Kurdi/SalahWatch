package com.salahwatch.app.widget

import com.salahwatch.core.model.Prayer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The strip's fixed slots against the domain.
 *
 * RemoteViews cannot add children at runtime, so the strip's column count lives in a layout file
 * and in five parallel id arrays. Nothing connects those to [Prayer] except this test — a sixth
 * prayer, or a column added to the XML and not to the arrays, would otherwise surface as a widget
 * silently missing an entry.
 */
class StripWidgetTest {

    @Test
    fun `there is one column slot per prayer, and Sunrise is not one of them`() {
        val prayers = Prayer.entries.count { it.isPrayer }

        assertEquals(5, prayers, "The strip's layout is built for exactly five prayers")
        assertEquals(prayers, StripIds.COLUMN.size)
    }

    @Test
    fun `every id array covers every column`() {
        val arrays = mapOf(
            "ICON" to StripIds.ICON,
            "NAME" to StripIds.NAME,
            "TIME" to StripIds.TIME,
            "STILL" to StripIds.STILL,
            "LIVE" to StripIds.LIVE,
        )

        for ((name, ids) in arrays) {
            assertEquals(StripIds.COLUMN.size, ids.size, "$name is missing a column")
            assertEquals(ids.size, ids.distinct().size, "$name reuses an id between columns")
        }
    }

    @Test
    fun `the schedule widget still covers all six entries`() {
        // Sunrise *is* shown there — the two widgets answer different questions.
        assertEquals(Prayer.entries.size, WidgetIds.ROW.size)
    }
}
