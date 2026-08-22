package com.salahwatch.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salahwatch.app.ui.DayArc
import com.salahwatch.app.ui.LocalPalette
import com.salahwatch.app.ui.TimeStyle
import com.salahwatch.core.calculation.PrayerOccurrence
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahDate

/**
 * Today: where you are in the day, what is next, and the full schedule.
 *
 * Ordered by what someone actually opens the app to find out. The clock and date come first because
 * they are the frame everything else is read against, the day arc second because it answers "how far
 * through the day am I" without reading a single number, and the schedule last because it is
 * reference rather than headline.
 */
@Composable
internal fun TodayScreen(
    state: HomeState,
    now: Instant,
    onRefresh: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    when (state) {
        HomeState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = LocalPalette.current.brass)
        }

        HomeState.NeedsLocation -> EmptyState()

        is HomeState.Ready -> ReadyContent(state, now, onRefresh, onSelectDate)
    }
}

/** An empty screen is an invitation to act, so it says what to do rather than what is missing. */
@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                stringResource(R.string.empty_location_title),
                style = MaterialTheme.typography.titleMedium,
                color = LocalPalette.current.ink,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.empty_location_body),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalPalette.current.inkMuted,
            )
        }
    }
}

/**
 * Two shapes of screen, sharing one list.
 *
 * On today, everything is live: the clock ticks, the arc fills, the next prayer counts down and the
 * prayers behind you are dimmed. On any other date none of that is true — a countdown to a Fajr
 * three weeks out is noise, and dimming "passed" prayers on a future day would be a lie. So those
 * pieces are omitted rather than frozen, and what remains is a timetable, which is exactly what is
 * being asked for when someone browses to a date.
 */
@Composable
private fun ReadyContent(
    state: HomeState.Ready,
    now: Instant,
    onRefresh: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val zone = state.prayers.firstOrNull()?.zone ?: state.next?.zone ?: ZoneId.systemDefault()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { ClockHeader(state.date, now, zone, state.isToday, state.locationLabel) }

        item { DateSelector(date = state.date, onSelect = onSelectDate) }

        if (state.isToday) {
            if (state.prayers.size >= 2) {
                item { DayArc(prayers = state.prayers, now = now) }
            }
            state.next?.let { item { NextPrayerPanel(it, now) } }
        }

        item { ScheduleHeader(onRefresh) }

        items(state.prayers) { occurrence ->
            PrayerRow(
                occurrence = occurrence,
                isNext = state.isToday && state.next?.let {
                    it.prayer == occurrence.prayer && it.adhan == occurrence.adhan
                } == true,
                hasPassed = state.isToday && occurrence.adhan < now,
            )
        }

        if (state.isEstimated) {
            item { EstimateNotice() }
        }
    }
}

/**
 * The live clock, both calendars, and where these times are for.
 *
 * The Hijri date comes from `java.time`'s Umm al-Qura calendar — so it costs no API call and cannot
 * drift out of step with the prayer times.
 * It is formatted in the app's language along with everything else, which in Arabic means the
 * month names appear as the calendar's own rather than transliterated.
 */
@Composable
private fun ClockHeader(
    date: LocalDate,
    now: Instant,
    zone: ZoneId,
    isToday: Boolean,
    locationLabel: String?,
) {
    val palette = LocalPalette.current
    val formats = formats()
    val local = now.atZone(zone)
    // Both calendars describe the day on screen. The clock only ever describes now, so on another
    // date it is dropped rather than shown against dates it does not belong to.
    val hijri = HijrahDate.from(date)

    Column {
        if (isToday) {
            Text(
                text = formats.clock.format(local),
                style = MaterialTheme.typography.displayMedium,
                color = palette.ink,
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = formats.gregorian.format(date),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.inkMuted,
        )
        Text(
            text = formats.hijri.format(hijri),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.brass,
        )
        locationLabel?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = it.uppercase(formats.locale),
                style = MaterialTheme.typography.labelSmall,
                color = palette.inkFaint,
            )
        }
    }
}

/** The next prayer, with the time remaining counting down live. */
@Composable
private fun NextPrayerPanel(next: PrayerOccurrence, now: Instant) {
    val palette = LocalPalette.current
    val formats = formats()

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surfaceRaised)
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(R.string.label_next),
            style = MaterialTheme.typography.labelMedium,
            color = palette.inkFaint,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = next.prayer.label(),
            style = MaterialTheme.typography.headlineSmall,
            color = palette.ink,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = formats.time.format(next.adhan.atZone(next.zone)),
            style = MaterialTheme.typography.displayLarge,
            color = palette.brass,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = remainingUntil(next.adhan, now),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.inkMuted,
        )
        next.iqama?.let {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.label_iqama),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkMuted,
                )
                Text(
                    formats.time.format(it.atZone(next.zone)),
                    style = TimeStyle,
                    color = palette.ink,
                )
            }
        }
    }
}

@Composable
private fun ScheduleHeader(onRefresh: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.label_schedule),
            style = MaterialTheme.typography.labelMedium,
            color = palette.inkFaint,
        )
        TextButton(onClick = onRefresh) {
            Text(stringResource(R.string.action_refresh), color = palette.brass)
        }
    }
}

/**
 * One prayer.
 *
 * Times are tabular, so the Adhan and Iqama columns align down the list. Prayers already passed are
 * dimmed rather than removed — you still want to see when Fajr was.
 */
@Composable
private fun PrayerRow(occurrence: PrayerOccurrence, isNext: Boolean, hasPassed: Boolean) {
    val palette = LocalPalette.current
    val formats = formats()

    val nameColour = when {
        isNext -> palette.brass
        hasPassed -> palette.inkFaint
        else -> palette.ink
    }
    val timeColour = if (hasPassed && !isNext) palette.inkFaint else palette.ink

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isNext) palette.surfaceRaised else palette.background)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = occurrence.prayer.label(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal,
            color = nameColour,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formats.time.format(occurrence.adhan.atZone(occurrence.zone)),
            style = TimeStyle,
            color = timeColour,
        )
        Text(
            // Sunrise has no congregation, so the column is blank rather than filled with a dash
            // that would read as missing data.
            text = occurrence.iqama
                ?.let { "   " + formats.time.format(it.atZone(occurrence.zone)) }
                ?: "",
            style = TimeStyle,
            color = palette.inkMuted,
        )
    }
}

/** Plain, specific, and in the interface's voice — it explains rather than apologises. */
@Composable
private fun EstimateNotice() {
    Text(
        text = stringResource(R.string.estimate_notice),
        style = MaterialTheme.typography.bodySmall,
        color = LocalPalette.current.inkFaint,
    )
}

/**
 * "in 5h 30m", narrowing to seconds as the prayer approaches.
 *
 * Hours and minutes rather than a rounded "6h": half an hour is a meaningful amount of time to
 * someone deciding whether to start something. Inside the last five minutes it switches to seconds,
 * where the exact remaining time is what you actually want.
 */
@Composable
private fun remainingUntil(target: Instant, now: Instant): String {
    val duration = Duration.between(now, target)
    if (duration.isNegative || duration.isZero) return stringResource(R.string.remaining_now)

    val hours = duration.toHours()
    val minutes = (duration.toMinutes() % MINUTES_PER_HOUR).toInt()
    val seconds = (duration.seconds % SECONDS_PER_MINUTE).toInt()

    return when {
        hours > 0 -> numericStringResource(R.string.remaining_hours, hours.toInt(), minutes)
        duration > SECONDS_THRESHOLD -> numericStringResource(R.string.remaining_minutes, minutes)
        minutes > 0 -> numericStringResource(R.string.remaining_minutes_seconds, minutes, seconds)
        else -> numericStringResource(R.string.remaining_seconds, seconds)
    }
}

private const val MINUTES_PER_HOUR = 60
private const val SECONDS_PER_MINUTE = 60

/** Where "how many minutes" stops being the useful question and "how long exactly" starts. */
private val SECONDS_THRESHOLD: Duration = Duration.ofMinutes(SECONDS_THRESHOLD_MINUTES)

private const val SECONDS_THRESHOLD_MINUTES = 5L
