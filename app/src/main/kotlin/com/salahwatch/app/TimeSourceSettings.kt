package com.salahwatch.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salahwatch.app.ui.LocalPalette
import com.salahwatch.app.ui.RadioRow
import com.salahwatch.app.ui.SettingsDivider
import com.salahwatch.app.ui.SettingsSection
import com.salahwatch.core.model.MosqueRef
import com.salahwatch.core.model.OfficialArea
import com.salahwatch.core.model.TimeSource
import com.salahwatch.core.model.effectiveLocation
import com.salahwatch.core.preferences.UserPreferences

/**
 * Where prayer times come from.
 *
 * Three genuinely different answers, so these are radio rows rather than a hidden default. The
 * area's official table is exact where one is bundled; a nearby mosque is the times a congregation
 * actually prays by; calculation works anywhere and approximates both.
 */
@Composable
internal fun TimeSourceSection(preferences: UserPreferences, viewModel: PrayerViewModel) {
    SettingsSection(
        title = stringResource(R.string.settings_source),
        description = stringResource(R.string.settings_source_desc),
    ) {
        val labels = TimeSource.entries.associateWith { it.label() to it.description() }
        TimeSource.entries.forEachIndexed { index, source ->
            if (index > 0) SettingsDivider()
            RadioRow(
                option = source,
                selected = preferences.timeSource,
                label = { labels.getValue(it).first },
                supporting = { labels.getValue(it).second },
                onSelect = viewModel.edit::timeSource,
            )
        }

        if (preferences.timeSource == TimeSource.OFFICIAL) {
            SettingsDivider()
            AreaPicker(preferences, viewModel)
        }

        if (preferences.timeSource == TimeSource.NEARBY_MOSQUE) {
            SettingsDivider()
            MosquePicker(preferences.mosque, viewModel)
        }
    }
}

/**
 * Which bundled timetable to follow.
 *
 * Shows the automatic choice as a selected row rather than leaving every row blank, so that a user
 * who never opens this screen can still see which city's table their times came from — the one
 * question a bundled timetable has to answer, since the wrong city looks entirely plausible.
 */
@Composable
private fun AreaPicker(preferences: UserPreferences, viewModel: PrayerViewModel) {
    val location = preferences.location.effectiveLocation
    val automatic = location?.let { OfficialArea.nearestTo(it.latitude, it.longitude) }
    val effective = preferences.officialArea ?: automatic

    // Resolved up front because RadioRow's label is a plain lambda, not a composable one.
    val names = OfficialArea.entries.associateWith { it.label() }

    Column(Modifier.padding(vertical = 4.dp)) {
        OfficialArea.entries.forEach { area ->
            RadioRow(
                option = area,
                selected = effective,
                label = { names.getValue(area) },
                // Bound to `area` rather than the row's value because the selection is nullable
                // (nothing is pinned yet) while the edit takes a definite area.
                onSelect = { viewModel.edit.officialArea(area) },
            )
        }

        val note = when {
            effective == null -> stringResource(R.string.area_none_nearby)
            preferences.officialArea == null -> stringResource(R.string.area_automatic)
            else -> null
        }
        note?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = LocalPalette.current.inkMuted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun MosquePicker(selected: MosqueRef?, viewModel: PrayerViewModel) {
    val state by viewModel.edit.mosqueFinder.state.collectAsStateWithLifecycle()

    // Look once when the section appears, rather than making the user press a button to discover
    // that the feature needs one.
    LaunchedEffect(Unit) { viewModel.edit.mosqueFinder.find() }

    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        when (val current = state) {
            NearbyMosques.Idle, NearbyMosques.Searching -> {
                ChosenElsewhere(selected)
                Note(stringResource(R.string.mosque_searching))
            }

            NearbyMosques.NeedsLocation ->
                Note(stringResource(R.string.mosque_needs_location))

            NearbyMosques.Failed -> {
                ChosenElsewhere(selected)
                Row {
                    Note(stringResource(R.string.mosque_search_failed))
                    TextButton(onClick = { viewModel.edit.mosqueFinder.find() }) {
                        Text(
                            stringResource(R.string.action_retry),
                            color = LocalPalette.current.brass,
                        )
                    }
                }
            }

            is NearbyMosques.Found ->
                if (current.mosques.isEmpty()) {
                    ChosenElsewhere(selected)
                    Note(stringResource(R.string.mosque_none_nearby))
                } else {
                    // The chosen mosque is a row in this list like any other, marked by being
                    // brass. It used to also be echoed as a heading above the list, which read as
                    // the same mosque appearing twice with only one of them selectable.
                    if (current.mosques.none { it.id == selected?.id }) ChosenElsewhere(selected)

                    current.mosques.forEach { mosque ->
                        MosqueRow(mosque, isSelected = mosque.id == selected?.id) {
                            viewModel.edit.mosque(mosque)
                        }
                    }
                }
        }
    }
}

/**
 * The mosque being followed, shown only when it is not one of the rows below.
 *
 * That happens after travelling, or when the directory is unreachable — cases where saying nothing
 * would leave the user unable to tell which mosque their times are coming from.
 */
@Composable
private fun ChosenElsewhere(selected: MosqueRef?) {
    val palette = LocalPalette.current
    selected ?: return

    Text(
        text = stringResource(R.string.mosque_following, selected.name),
        style = MaterialTheme.typography.bodyLarge,
        color = palette.brass,
    )
    selected.locality?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkFaint)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun MosqueRow(mosque: MosqueRef, isSelected: Boolean, onSelect: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = mosque.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) palette.brass else palette.ink,
        )
        Text(
            text = listOfNotNull(mosque.locality, mosque.distanceMetres?.let { distanceLabel(it) })
                .joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkFaint,
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = LocalPalette.current.inkMuted,
    )
}

/** Metres below a kilometre, kilometres above — nobody reads "1400 m". */
private fun distanceLabel(metres: Int): String =
    if (metres < METRES_PER_KM) "$metres m" else "${metres / METRES_PER_KM} km"

private const val METRES_PER_KM = 1000
