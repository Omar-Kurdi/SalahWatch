package com.salahwatch.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.salahwatch.app.ui.Dropdown
import com.salahwatch.app.ui.MinuteStepper
import com.salahwatch.app.ui.RadioRow
import com.salahwatch.app.ui.SettingsDivider
import com.salahwatch.app.ui.SettingsSection
import com.salahwatch.core.calculation.AlAdhanMethods
import com.salahwatch.core.calculation.MethodParameters
import com.salahwatch.core.model.AppLanguage
import com.salahwatch.core.model.CalculationSettings
import com.salahwatch.core.model.ThemeMode
import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.School
import com.salahwatch.core.model.Shafaq
import com.salahwatch.core.preferences.UserPreferences

/**
 * Everything configurable.
 *
 * Sections are ordered by how often they are touched and how much they change: location first
 * because nothing works without it, then the calculation inputs, then the fine corrections that most
 * people will never open.
 */
@Composable
internal fun SettingsScreen(preferences: UserPreferences, viewModel: PrayerViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item { TimeSourceSection(preferences, viewModel) }
        item { LocationSection(preferences, viewModel) }
        item { AppearanceSection(preferences, viewModel) }
        item { LanguageSection(preferences, viewModel) }
        item { MethodSection(preferences.calculation, viewModel) }
        item { SchoolSection(preferences.calculation, viewModel) }

        if (preferences.calculation.isShafaqRelevant) {
            item { ShafaqSection(preferences.calculation, viewModel) }
        }

        item { IqamaSection(preferences, viewModel) }
        item { AdjustmentsSection(preferences.calculation, viewModel) }
    }
}

/**
 * Twenty-three options, so a dropdown.
 *
 * The angles are shown alongside each name because they are what actually distinguishes the methods
 * — "Muslim World League" tells you nothing on its own, "18° / 17°" tells you what it will do.
 */
@Composable
private fun MethodSection(settings: CalculationSettings, viewModel: PrayerViewModel) {
    SettingsSection(
        title = stringResource(R.string.settings_method),
        description = stringResource(R.string.settings_method_desc),
    ) {
        Dropdown(
            label = stringResource(R.string.settings_method),
            options = AlAdhanMethods.all,
            selected = AlAdhanMethods[settings.methodId] ?: AlAdhanMethods.all.first(),
            optionLabel = ::methodLabel,
            onSelect = { method -> viewModel.edit.calculation { it.copy(methodId = method.id) } },
        )
    }
}

private fun methodLabel(method: MethodParameters): String {
    val isha = method.ishaAngle?.let { "${trim(it)}°" } ?: "${method.ishaIntervalMinutes} min"
    return "${method.name}  ·  ${trim(method.fajrAngle)}° / $isha"
}

private fun trim(value: Double): String =
    if (value == value.toInt().toDouble()) value.toInt().toString() else value.toString()

/** Two options, so radio buttons — and each says what it actually does to Asr. */
@Composable
private fun SchoolSection(settings: CalculationSettings, viewModel: PrayerViewModel) {
    SettingsSection(
        title = stringResource(R.string.settings_school),
        description = stringResource(R.string.settings_school_desc),
    ) {
        val labels = School.entries.associateWith { it.label() to it.description() }
        School.entries.forEachIndexed { index, school ->
            if (index > 0) SettingsDivider()
            RadioRow(
                option = school,
                selected = settings.school,
                label = { labels.getValue(it).first },
                supporting = { labels.getValue(it).second },
                onSelect = { picked -> viewModel.edit.calculation { it.copy(school = picked) } },
            )
        }
    }
}

/**
 * Only rendered for the Moonsighting method.
 *
 * Shafaq has no effect on any other method, so showing it always would be a control that silently
 * does nothing — worse than not offering it.
 */
@Composable
private fun ShafaqSection(settings: CalculationSettings, viewModel: PrayerViewModel) {
    SettingsSection(
        title = stringResource(R.string.settings_twilight),
        description = stringResource(R.string.settings_twilight_desc),
    ) {
        val labels = Shafaq.entries.associateWith { it.label() to it.description() }
        Shafaq.entries.forEachIndexed { index, shafaq ->
            if (index > 0) SettingsDivider()
            RadioRow(
                option = shafaq,
                selected = settings.shafaq,
                label = { labels.getValue(it).first },
                supporting = { labels.getValue(it).second },
                onSelect = { picked -> viewModel.edit.calculation { it.copy(shafaq = picked) } },
            )
        }
    }
}

@Composable
private fun IqamaSection(preferences: UserPreferences, viewModel: PrayerViewModel) {
    val iqama = preferences.iqama
    androidx.compose.foundation.layout.Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
    SettingsSection(
        title = stringResource(R.string.settings_iqama_source),
    ) {
        val labels = selectableIqamaModes.associateWith { it.label() to it.description() }
        selectableIqamaModes.forEachIndexed { index, mode ->
            if (index > 0) SettingsDivider()
            RadioRow(
                option = mode,
                selected = iqama.mode,
                label = { labels.getValue(it).first },
                supporting = { labels.getValue(it).second },
                onSelect = { picked -> viewModel.edit.iqama { it.copy(mode = picked) } },
            )
        }
    }

    // The offsets stay visible in either mode: they are what "my own offsets" means, and seeing
    // them while the mosque's are in use makes the difference concrete rather than abstract.
    SettingsSection(
        title = stringResource(R.string.settings_iqama),
        description = stringResource(R.string.settings_iqama_desc),
    ) {
        // Each stepper reports a delta and the new total is computed from whatever is stored at
        // that moment, never from the value this composition happens to be holding. Reading the
        // displayed value here would lose taps that arrive faster than the write completes.
        MinuteStepper(Prayer.FAJR.label(), iqama.fajrOffset, allowNegative = false) { d ->
            viewModel.edit.iqama { it.copy(fajrOffset = it.fajrOffset.stepped(d)) }
        }
        MinuteStepper(Prayer.DHUHR.label(), iqama.dhuhrOffset, allowNegative = false) { d ->
            viewModel.edit.iqama { it.copy(dhuhrOffset = it.dhuhrOffset.stepped(d)) }
        }
        MinuteStepper(Prayer.ASR.label(), iqama.asrOffset, allowNegative = false) { d ->
            viewModel.edit.iqama { it.copy(asrOffset = it.asrOffset.stepped(d)) }
        }
        MinuteStepper(Prayer.MAGHRIB.label(), iqama.maghribOffset, allowNegative = false) { d ->
            viewModel.edit.iqama { it.copy(maghribOffset = it.maghribOffset.stepped(d)) }
        }
        MinuteStepper(Prayer.ISHA.label(), iqama.ishaOffset, allowNegative = false) { d ->
            viewModel.edit.iqama { it.copy(ishaOffset = it.ishaOffset.stepped(d)) }
        }
    }
    }
}

/** An Iqama offset moved by [delta]. Never negative: a congregation cannot precede its Adhan. */
private fun Int.stepped(delta: Int): Int = (this + delta).coerceAtLeast(0)

/**
 * Corrections against a local timetable.
 *
 * The description names the use case rather than the mechanism, because that is how someone arrives
 * here: they noticed the app disagrees with the timetable on the mosque wall.
 *
 * Goes through `edit.adjustments` rather than `edit.calculation`, which is what makes a tap here
 * take effect immediately. Corrections are applied when times are read, so nothing needs refetching
 * — routing them through the general calculation command queued a fetch of the whole year on every
 * single tap and showed nothing until it landed.
 */
@Composable
private fun AdjustmentsSection(settings: CalculationSettings, viewModel: PrayerViewModel) {
    val adjustments = settings.adjustments
    SettingsSection(
        title = stringResource(R.string.settings_corrections),
        description = stringResource(R.string.settings_corrections_desc),
    ) {
        for (prayer in Prayer.entries) {
            MinuteStepper(
                label = prayer.label(),
                value = adjustments[prayer],
                allowNegative = true,
            ) { delta ->
                viewModel.edit.adjustments { it.movedBy(prayer, delta) }
            }
        }
    }
}

