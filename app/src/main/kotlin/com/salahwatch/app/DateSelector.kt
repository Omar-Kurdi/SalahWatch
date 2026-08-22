package com.salahwatch.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salahwatch.app.ui.LocalPalette
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Which day the schedule is showing, and how to change it.
 *
 * Two ways, because there are two different intentions. Stepping a day at a time answers "and
 * tomorrow?", which is the common case and should cost one tap. The full month grid answers "what
 * time is Fajr on the 14th", which is a jump, and scrubbing to it one day at a time would be
 * absurd. The arrows stop dead at the ends of the range rather than wrapping or silently doing
 * nothing, so the boundary is visible before it is reached.
 */
@Composable
internal fun DateSelector(
    date: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val formats = formats()
    val today = remember { LocalDate.now() }
    val range = remember(today) { browsableRange(today) }
    var picking by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Glyphs rather than icons: the app ships no icon pack, and an arrow is one character that
        // mirrors correctly under RTL along with the rest of the row.
        StepButton(
            glyph = "‹",
            description = stringResource(R.string.action_previous_day),
            enabled = date > range.first,
            onClick = { onSelect(date.minusDays(1)) },
        )

        Text(
            text = dateLabel(date, today, formats),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (date == today) palette.brass else palette.ink,
            modifier = Modifier
                .clickable(role = Role.Button) { picking = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )

        StepButton(
            glyph = "›",
            description = stringResource(R.string.action_next_day),
            enabled = date < range.second,
            onClick = { onSelect(date.plusDays(1)) },
        )
    }

    if (picking) {
        MonthGrid(
            date = date,
            range = range,
            onDismiss = { picking = false },
            onSelect = {
                picking = false
                onSelect(it)
            },
        )
    }
}

/**
 * An arrow.
 *
 * The glyph is decoration as far as a screen reader is concerned — "\u2039" would be announced as
 * punctuation or skipped entirely — so the button carries a real name in [description] and the
 * text itself is cleared out of the semantics tree.
 */
@Composable
private fun StepButton(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = glyph,
                style = MaterialTheme.typography.titleMedium,
                // Disabled is dimmed rather than hidden, so the control does not jump about as the
                // user reaches the end of the range.
                color = if (enabled) palette.brass else palette.inkFaint,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/**
 * The month grid, constrained to the browsable range.
 *
 * Dates outside it are shown but not selectable — greying them is more informative than a grid that
 * simply ends, because it says "this app does not go further" rather than "the calendar stops here".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthGrid(
    date: LocalDate,
    range: Pair<LocalDate, LocalDate>,
    onDismiss: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val palette = LocalPalette.current
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.toUtcMillis(),
        initialDisplayedMonthMillis = date.toUtcMillis(),
        yearRange = range.first.year..range.second.year,
        selectableDates = remember(range) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis.toLocalDate() in range.first..range.second

                override fun isSelectableYear(year: Int): Boolean =
                    year in range.first.year..range.second.year
            }
        },
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = DatePickerDefaults.colors(containerColor = palette.surface),
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onSelect(it.toLocalDate()) } },
                enabled = state.selectedDateMillis != null,
            ) {
                Text(stringResource(R.string.action_set), color = palette.brass)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = palette.inkMuted)
            }
        },
    ) {
        DatePicker(
            state = state,
            title = null,
            headline = null,
            showModeToggle = false,
            colors = DatePickerDefaults.colors(
                containerColor = palette.surface,
                selectedDayContainerColor = palette.brass,
                selectedDayContentColor = palette.background,
                todayContentColor = palette.brass,
                todayDateBorderColor = palette.brass,
            ),
        )
    }
}

/** "Today" beats a date the reader has to compare against one they already know. */
@Composable
private fun dateLabel(date: LocalDate, today: LocalDate, formats: Formats): String = when (date) {
    today -> stringResource(R.string.date_today)
    today.minusDays(1) -> stringResource(R.string.date_yesterday)
    today.plusDays(1) -> stringResource(R.string.date_tomorrow)
    else -> formats.date.format(date)
}

/** How far the picker reaches, resolved against the view model's own limit. */
internal fun browsableRange(today: LocalDate): Pair<LocalDate, LocalDate> =
    today.minusMonths(PrayerViewModel.BROWSE_MONTHS) to today.plusMonths(PrayerViewModel.BROWSE_MONTHS)

/**
 * Material's date picker speaks in UTC milliseconds, and this app speaks in local dates.
 *
 * The conversion goes through [ZoneOffset.UTC] on purpose. Using the device zone here is the
 * classic date-picker bug: east of Greenwich the day the user tapped comes back as the day before.
 */
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate =
    java.time.Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
