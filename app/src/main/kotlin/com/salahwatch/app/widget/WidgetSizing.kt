package com.salahwatch.app.widget

/**
 * The space a widget has been given, in dp.
 *
 * Resolved from `AppWidgetManager.getAppWidgetOptions`, whose four values are easy to misread. The
 * pairing is **`MIN_WIDTH`/`MAX_HEIGHT` = portrait** and **`MAX_WIDTH`/`MIN_HEIGHT` = landscape**,
 * not "min and max of a single box". Taking the minimum of each *axis* gives the box that is
 * available in both orientations, which is what a single rendering needs — taking `MIN_WIDTH` and
 * `MIN_HEIGHT` together would describe a box narrower and shorter than the widget ever actually is.
 */
internal data class WidgetSize(val widthDp: Int, val heightDp: Int) {
    /**
     * True when the launcher has not said yet.
     *
     * The options bundle is empty on the first `ACTION_APPWIDGET_UPDATE` after placement, and
     * sometimes after a reboot, so both values read back as zero. That must not be mistaken for a
     * tiny widget — see [gridLayoutFor].
     */
    val isUnknown: Boolean get() = widthDp <= 0 || heightDp <= 0

    internal companion object {
        /** The conservative box: what is available in portrait *and* landscape. */
        fun of(minWidth: Int, maxWidth: Int, minHeight: Int, maxHeight: Int): WidgetSize =
            WidgetSize(
                widthDp = minOf(minWidth, maxWidth).coerceAtLeast(0),
                heightDp = minOf(minHeight, maxHeight).coerceAtLeast(0),
            )
    }
}

/** How much room the six rows have to breathe in. */
internal enum class WidgetDensity { COMPACT, MEDIUM, FULL }

/**
 * How the schedule widget draws itself at a given size.
 *
 * The widget is resizable from roughly two cells square up to whatever the launcher allows, and six
 * rows plus a countdown do not fit in the smaller of those. Rather than clipping — which is what a
 * fixed layout did, losing Isha off the bottom — the same layout is scaled: chrome is dropped first,
 * then type and padding come down, and the six rows themselves are never sacrificed. The rows are
 * the widget; a timetable missing its last prayer is worse than a small one.
 */
internal data class GridLayout(
    val density: WidgetDensity,
    /** The date and location line. First to go: it repeats what the phone's status bar says. */
    val showHeader: Boolean,
    val showDivider: Boolean,
    /** Dropped on narrow widgets, where two time columns will not fit legibly. */
    val showIqama: Boolean,
    val showNotice: Boolean,
    val rowTextSp: Float,
    val countdownTextSp: Float,
    val prayerTextSp: Float,
    val rowPaddingDp: Int,
)

/**
 * Picks a layout for [size].
 *
 * An unknown size resolves to the largest layout, deliberately. Guessing small on a widget that
 * turns out to be large is a permanent-looking defect — cramped type on a four-cell widget — while
 * guessing large on a small one is corrected within a frame, because the launcher follows an empty
 * options bundle with `onAppWidgetOptionsChanged` carrying the real numbers.
 */
internal fun gridLayoutFor(size: WidgetSize): GridLayout {
    if (size.isUnknown) return FULL_GRID

    val showIqama = size.widthDp >= IQAMA_COLUMN_MIN_WIDTH_DP

    return when {
        size.heightDp >= FULL_MIN_HEIGHT_DP -> FULL_GRID.copy(showIqama = showIqama)

        size.heightDp >= MEDIUM_MIN_HEIGHT_DP -> GridLayout(
            density = WidgetDensity.MEDIUM,
            showHeader = false,
            showDivider = true,
            showIqama = showIqama,
            showNotice = false,
            rowTextSp = 12f,
            countdownTextSp = 22f,
            prayerTextSp = 15f,
            rowPaddingDp = 2,
        )

        else -> GridLayout(
            density = WidgetDensity.COMPACT,
            showHeader = false,
            showDivider = false,
            showIqama = showIqama,
            showNotice = false,
            rowTextSp = 10f,
            countdownTextSp = 18f,
            prayerTextSp = 13f,
            rowPaddingDp = 0,
        )
    }
}

private val FULL_GRID = GridLayout(
    density = WidgetDensity.FULL,
    showHeader = true,
    showDivider = true,
    showIqama = true,
    showNotice = true,
    rowTextSp = 14f,
    countdownTextSp = 26f,
    prayerTextSp = 18f,
    rowPaddingDp = 3,
)

/**
 * How the one-row strip draws itself.
 *
 * The strip is a single cell tall and cannot scale by dropping rows, so the only lever is type. The
 * AM/PM marker is the one piece that goes, and only at the narrowest sizes: prayer times are
 * unambiguous in practice — nobody reads Fajr as an evening time — but it is still information, so
 * it stays wherever there is room for it.
 */
internal data class StripLayout(
    val showMeridiem: Boolean,
    val nameTextSp: Float,
    val timeTextSp: Float,
    val countdownTextSp: Float,
    val iconSizeDp: Int,
)

internal fun stripLayoutFor(size: WidgetSize): StripLayout {
    if (size.isUnknown) return FULL_STRIP

    return when {
        size.widthDp >= STRIP_WIDE_MIN_WIDTH_DP -> FULL_STRIP

        size.widthDp >= STRIP_MEDIUM_MIN_WIDTH_DP -> StripLayout(
            showMeridiem = true,
            nameTextSp = 9f,
            timeTextSp = 11f,
            countdownTextSp = 10f,
            iconSizeDp = 14,
        )

        else -> StripLayout(
            showMeridiem = false,
            nameTextSp = 8f,
            timeTextSp = 10f,
            countdownTextSp = 9f,
            iconSizeDp = 12,
        )
    }
}

private val FULL_STRIP = StripLayout(
    showMeridiem = true,
    nameTextSp = 10f,
    timeTextSp = 12f,
    countdownTextSp = 11f,
    iconSizeDp = 16,
)

/**
 * Three cells tall or more, at the ~70dp cell most launchers use.
 *
 * This is the size the widget is meant to be lived with: the header line, the countdown, all six
 * rows and the estimate notice. Three cells square is enough for it — the original four-cell target
 * asked for more room than the content needs.
 */
private const val FULL_MIN_HEIGHT_DP = 200

/** Between two and three cells: the countdown and all six rows, without the chrome around them. */
private const val MEDIUM_MIN_HEIGHT_DP = 150

/** Below this — under about three cells — a second column of times crowds out the prayer names. */
private const val IQAMA_COLUMN_MIN_WIDTH_DP = 190

/** Four cells or more: five columns with room for `4:35 AM` in each. */
private const val STRIP_WIDE_MIN_WIDTH_DP = 250

/** Three cells. Below this the meridiem has to go. */
private const val STRIP_MEDIUM_MIN_WIDTH_DP = 190
