package com.salahwatch.wear

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.salahwatch.core.calculation.PrayerCalendar
import com.salahwatch.core.calculation.PrayerOccurrence
import com.salahwatch.core.database.ScheduleStore
import com.salahwatch.core.preferences.LanguageOverride
import com.salahwatch.core.preferences.PreferencesRepository
import com.salahwatch.data.prayertimes.PrayerScheduleRepository
import com.salahwatch.wear.ui.PrayerRing
import com.salahwatch.wear.ui.WearColors
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * The watch app, opened by tapping any complication.
 *
 * Built for a glance, in the order a glance wants it: what is next and how long, then the day's
 * shape on the bezel, then the full schedule for anyone who scrolls.
 *
 * There is deliberately no wall clock here — the watch face already shows one, and duplicating it
 * would spend the screen's only large type slot on information the user is already looking at.
 */
class MainActivity : ComponentActivity() {

    private val repository by lazy {
        PrayerScheduleRepository(
            dao = ScheduleStore.dao(applicationContext),
            preferences = PreferencesRepository(applicationContext),
        )
    }

    /** The language chosen on the phone, mirrored here by the sync listener. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageOverride.attach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val calendar by produceState<PrayerCalendar?>(initialValue = null) {
                    value = repository.timelineCalendar(LocalDate.now())
                }
                PrayerScreen(calendar, rememberCurrentInstant())
            }
        }
    }
}

/**
 * Ticks once a second, and only while composed.
 *
 * A watch app is on screen for a few seconds at a time, so a live countdown costs almost nothing,
 * and `produceState` cancels the loop the moment the screen is dismissed.
 */
@Composable
private fun rememberCurrentInstant(): Instant =
    produceState(initialValue = Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(TICK_MILLIS)
        }
    }.value

@Composable
private fun PrayerScreen(calendar: PrayerCalendar?, now: Instant) {
    val listState = rememberScalingLazyListState()
    val next = calendar?.nextPrayer(now)
    val today = calendar?.occurrences.orEmpty().filter { it.date == next?.date }

    Box(
        Modifier
            .fillMaxSize()
            .background(WearColors.Night),
    ) {
        if (today.isNotEmpty()) {
            PrayerRing(prayers = today, now = now)
        }

        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (next == null) {
                item { NoLocation() }
            } else {
                item { NextPrayer(next, now) }
                item { DateLine(next) }
                item { Spacer(Modifier.height(8.dp)) }
                items(today) { occurrence ->
                    ScheduleRow(occurrence, isNext = occurrence.adhan == next.adhan)
                }
            }
        }
    }
}

/** An empty state that says what to do, not what is missing. */
@Composable
private fun NoLocation() {
    Text(
        text = stringResource(R.string.watch_no_location),
        style = MaterialTheme.typography.bodyMedium,
        color = WearColors.InkMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

/** The one thing worth raising your wrist for: which prayer, when, and how long. */
@Composable
private fun NextPrayer(next: PrayerOccurrence, now: Instant) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = prayerName(next.prayer),
            style = MaterialTheme.typography.labelMedium,
            color = WearColors.InkMuted,
        )
        Text(
            text = watchFormats().time.format(next.adhan.atZone(next.zone)),
            style = MaterialTheme.typography.displayMedium,
            fontFamily = FontFamily.Monospace,
            color = WearColors.Brass,
        )
        Text(
            text = remainingUntil(next.adhan, now),
            style = MaterialTheme.typography.bodySmall,
            color = WearColors.InkMuted,
        )
        next.iqama?.let {
            Text(
                text = "${stringResource(R.string.label_iqama)} " +
                    watchFormats().time.format(it.atZone(next.zone)),
                style = MaterialTheme.typography.bodySmall,
                color = WearColors.InkFaint,
            )
        }
    }
}

/** Both calendars, so the day is unambiguous just after midnight. */
@Composable
private fun DateLine(next: PrayerOccurrence) {
    val local = next.adhan.atZone(next.zone)
    val formats = watchFormats()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formats.date.format(local),
            style = MaterialTheme.typography.labelSmall,
            color = WearColors.InkFaint,
        )
        Text(
            text = formats.hijri.format(HijrahDate.from(LocalDate.from(local))),
            style = MaterialTheme.typography.labelSmall,
            color = WearColors.InkFaint,
        )
    }
}

@Composable
private fun ScheduleRow(occurrence: PrayerOccurrence, isNext: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = prayerName(occurrence.prayer),
            style = MaterialTheme.typography.bodySmall,
            color = if (isNext) WearColors.Brass else WearColors.InkMuted,
        )
        Text(
            text = watchFormats().time.format(occurrence.adhan.atZone(occurrence.zone)),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = if (isNext) WearColors.Brass else WearColors.Ink,
        )
    }
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

/** The prayer's translated name. Kept here so the domain type stays free of resources. */
@Composable
private fun prayerName(prayer: com.salahwatch.core.model.Prayer): String = stringResource(
    when (prayer) {
        com.salahwatch.core.model.Prayer.FAJR -> R.string.prayer_fajr
        com.salahwatch.core.model.Prayer.SUNRISE -> R.string.prayer_sunrise
        com.salahwatch.core.model.Prayer.DHUHR -> R.string.prayer_dhuhr
        com.salahwatch.core.model.Prayer.ASR -> R.string.prayer_asr
        com.salahwatch.core.model.Prayer.MAGHRIB -> R.string.prayer_maghrib
        com.salahwatch.core.model.Prayer.ISHA -> R.string.prayer_isha
    },
)

private const val MINUTES_PER_HOUR = 60
private const val SECONDS_PER_MINUTE = 60

/** Where "how many minutes" stops being the useful question and "how long exactly" starts. */
private val SECONDS_THRESHOLD: Duration = Duration.ofMinutes(SECONDS_THRESHOLD_MINUTES)

private const val SECONDS_THRESHOLD_MINUTES = 5L
private const val TICK_MILLIS = 1_000L
