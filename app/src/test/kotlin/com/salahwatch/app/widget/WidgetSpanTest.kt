package com.salahwatch.app.widget

import java.io.File
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The two ways a widget declares its size must agree.
 *
 * `targetCellWidth`/`targetCellHeight` are honoured from API 31; `minWidth`/`minHeight` size the
 * placement before that, and some launchers take the larger of the two regardless. The platform
 * derives a span from the dp values as `ceil((size + 30) / 70)`, so the two declarations can
 * silently contradict each other — which is precisely what happened: a `minHeight` of 90dp derived
 * to two rows and the strip was placed 5x2 while `targetCellHeight` said 1, with nothing in the
 * build, detekt or lint noticing.
 */
class WidgetSpanTest {

    @Test
    fun `the strip is declared one row tall by both measures`() {
        assertSpansAgree("prayer_strip_widget_info.xml", columns = 5, rows = 1)
    }

    @Test
    fun `the schedule widget is declared three cells square by both measures`() {
        assertSpansAgree("prayer_widget_info.xml", columns = 3, rows = 3)
    }

    private fun assertSpansAgree(fileName: String, columns: Int, rows: Int) {
        val xml = read(fileName)

        assertEquals(columns, xml.attr("targetCellWidth").toInt(), "$fileName targetCellWidth")
        assertEquals(rows, xml.attr("targetCellHeight").toInt(), "$fileName targetCellHeight")

        assertEquals(
            columns,
            spanFor(xml.dp("minWidth")),
            "$fileName minWidth derives to a different column count than targetCellWidth",
        )
        assertEquals(
            rows,
            spanFor(xml.dp("minHeight")),
            "$fileName minHeight derives to a different row count than targetCellHeight",
        )
    }

    /** The platform's own rule for turning a dp size into a number of home screen cells. */
    private fun spanFor(dp: Int): Int = ceil((dp + CELL_PADDING_DP) / CELL_DP).toInt()

    private fun String.attr(name: String): String =
        requireNotNull(Regex("""android:$name="([^"]+)"""").find(this)) { "no $name" }
            .groupValues[1]

    private fun String.dp(name: String): Int = attr(name).removeSuffix("dp").toInt()

    private fun read(fileName: String): String {
        val file = sequenceOf(
            File("src/main/res/xml/$fileName"),
            File("app/src/main/res/xml/$fileName"),
        ).firstOrNull { it.exists() }

        return requireNotNull(file) { "Could not find $fileName from ${File(".").absolutePath}" }
            .readText()
    }

    private companion object {
        const val CELL_DP = 70.0
        const val CELL_PADDING_DP = 30.0
    }
}
