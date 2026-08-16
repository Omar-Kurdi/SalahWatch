package com.salahwatch.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.salahwatch.app.ui.RadioRow
import com.salahwatch.app.ui.SettingsDivider
import com.salahwatch.app.ui.SettingsSection
import com.salahwatch.core.model.AppLanguage
import com.salahwatch.core.model.ThemeMode
import com.salahwatch.core.preferences.UserPreferences

/**
 * Light or dark.
 *
 * Only the phone offers the choice: the watch app is always dark, because on an AMOLED display a
 * black pixel is an unlit pixel and a light theme would spend battery on a screen that is glanced
 * at rather than read. The description says so rather than leaving the absence unexplained.
 */
@Composable
internal fun AppearanceSection(preferences: UserPreferences, viewModel: PrayerViewModel) {
    SettingsSection(
        title = stringResource(R.string.settings_appearance),
        description = stringResource(R.string.settings_appearance_desc),
    ) {
        val labels = ThemeMode.entries.associateWith { it.label() }
        ThemeMode.entries.forEachIndexed { index, mode ->
            if (index > 0) SettingsDivider()
            RadioRow(
                option = mode,
                selected = preferences.theme,
                label = { labels.getValue(it) },
                onSelect = viewModel.edit::theme,
            )
        }
    }
}

@Composable
internal fun LanguageSection(preferences: UserPreferences, viewModel: PrayerViewModel) {
    SettingsSection(title = stringResource(R.string.settings_language)) {
        val labels = AppLanguage.entries.associateWith { it.label() }
        AppLanguage.entries.forEachIndexed { index, language ->
            if (index > 0) SettingsDivider()
            RadioRow(
                option = language,
                selected = preferences.language,
                label = { labels.getValue(it) },
                onSelect = viewModel.edit::language,
            )
        }
    }
}
