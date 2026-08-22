package com.salahwatch.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.salahwatch.app.ui.LocalPalette
import com.salahwatch.app.ui.PrayerColors
import com.salahwatch.app.ui.RadioRow
import com.salahwatch.app.ui.SettingsDivider
import com.salahwatch.app.ui.SettingsSection
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.salahwatch.core.model.LocationMode
import com.salahwatch.data.location.DeviceLocationProvider
import com.salahwatch.core.model.effectiveLocation
import com.salahwatch.core.preferences.UserPreferences
import kotlinx.coroutines.launch

/** How the location is chosen. Two modes, so radio buttons. */
private enum class LocationChoice(val label: Int, val supporting: Int) {
    AUTOMATIC(R.string.location_automatic, R.string.location_automatic_desc),
    MANUAL(R.string.location_manual, R.string.location_manual_desc),
}

/**
 * Choosing where prayer times are calculated for.
 *
 * The mode is a radio pair; the manual entry beneath it appears only when it applies. City lookup
 * comes first because typing a name is easier than knowing coordinates, but coordinates stay
 * available as the route that always works — they need neither permission nor a geocoding backend.
 */
@Composable
internal fun LocationSection(preferences: UserPreferences, viewModel: PrayerViewModel) {
    val choice = when (preferences.location) {
        is LocationMode.Automatic -> LocationChoice.AUTOMATIC
        is LocationMode.Manual -> LocationChoice.MANUAL
    }
    var pending by remember { mutableStateOf(choice) }
    val current = preferences.location.effectiveLocation

    // Choosing "use my location" is the *only* moment this app has any business asking for the
    // permission, so it asks here rather than on launch. Without this the option silently did
    // nothing on a fresh install: the manifest declares ACCESS_COARSE_LOCATION and the provider
    // checks it, but nothing ever requested it, leaving the system settings screen as the only way
    // to grant it — which no one should have to discover.
    val requestLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.edit.location.automatic()
        } else {
            // Denied. Fall back to manual rather than leaving the radio pointing at a mode that
            // cannot work — and manual needs no permission, so it is a real answer, not a dead end.
            pending = LocationChoice.MANUAL
        }
    }

    SettingsSection(
        title = stringResource(R.string.settings_location),
        description = current?.label ?: current?.let { "%.4f, %.4f".format(it.latitude, it.longitude) },
    ) {
        val labels = LocationChoice.entries.associateWith {
            stringResource(it.label) to stringResource(it.supporting)
        }
        LocationChoice.entries.forEachIndexed { index, option ->
            if (index > 0) SettingsDivider()
            RadioRow(
                option = option,
                selected = pending,
                label = { labels.getValue(it).first },
                supporting = { labels.getValue(it).second },
                onSelect = {
                    pending = it
                    if (it == LocationChoice.AUTOMATIC) {
                        if (viewModel.capabilities.canUseDeviceLocation) {
                            viewModel.edit.location.automatic()
                        } else {
                            requestLocation.launch(DeviceLocationProvider.REQUIRED_PERMISSION)
                        }
                    }
                },
            )
        }

        if (pending == LocationChoice.MANUAL) {
            SettingsDivider()
            ManualEntry(viewModel)
        }
    }
}

@Composable
private fun ManualEntry(viewModel: PrayerViewModel) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (viewModel.capabilities.canLookUpCities) {
            CityLookup(viewModel)
            Spacer(Modifier.height(12.dp))
        }
        CoordinateEntry(viewModel)
    }
}

/** Typing a name is easier than knowing coordinates, so this route comes first. */
@Composable
private fun CityLookup(viewModel: PrayerViewModel) {
    val scope = rememberCoroutineScope()
    var city by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val notFoundMessage = stringResource(R.string.error_city_not_found, city)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Field(
            value = city,
            onValueChange = { city = it; error = null },
            label = stringResource(R.string.field_city),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        BrassButton(
            label = stringResource(R.string.action_find),
            enabled = city.isNotBlank(),
            onClick = {
                scope.launch {
                    error = if (viewModel.edit.location.city(city)) {
                        null
                    } else {
                        notFoundMessage
                    }
                }
            },
        )
    }

    // Errors say what happened and what to do next, in the interface's voice. Warning is the one
    // colour shared by both themes: it is a warm orange chosen to be legible on either ground.
    error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = PrayerColors.Warning)
    }
}

/** The route that always works: no permission, no geocoding backend, nothing to be unavailable. */
@Composable
private fun CoordinateEntry(viewModel: PrayerViewModel) {
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Field(
            value = latitude,
            onValueChange = { latitude = it },
            label = stringResource(R.string.field_latitude),
            numeric = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Field(
            value = longitude,
            onValueChange = { longitude = it },
            label = stringResource(R.string.field_longitude),
            numeric = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        BrassButton(
            label = stringResource(R.string.action_set),
            enabled = latitude.toDoubleOrNull() != null && longitude.toDoubleOrNull() != null,
            onClick = {
                val lat = latitude.toDoubleOrNull() ?: return@BrassButton
                val lon = longitude.toDoubleOrNull() ?: return@BrassButton
                viewModel.edit.location.manual(lat, lon, null)
            },
        )
    }
}

@Composable
private fun BrassButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Button(
        enabled = enabled,
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.brass,
            contentColor = palette.background,
        ),
    ) { Text(label) }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
) {
    val palette = LocalPalette.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = if (numeric) {
            KeyboardOptions(keyboardType = KeyboardType.Decimal)
        } else {
            KeyboardOptions.Default
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = palette.ink,
            unfocusedTextColor = palette.ink,
            focusedBorderColor = palette.brass,
            unfocusedBorderColor = palette.line,
            focusedLabelColor = palette.brass,
            unfocusedLabelColor = palette.inkFaint,
        ),
        modifier = modifier,
    )
}
