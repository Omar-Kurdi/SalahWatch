package com.salahwatch.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.salahwatch.core.model.AppLanguage
import com.salahwatch.core.model.IqamaMode
import com.salahwatch.core.model.ThemeMode
import com.salahwatch.core.model.OfficialArea
import com.salahwatch.core.model.TimeSource

/** Display names for the app's own settings, as opposed to the prayer domain's types. */
@Composable
internal fun ThemeMode.label(): String = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
internal fun AppLanguage.label(): String = stringResource(
    when (this) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.ENGLISH -> R.string.language_english
        AppLanguage.ARABIC -> R.string.language_arabic
    },
)

@Composable
internal fun TimeSource.label(): String = stringResource(
    when (this) {
        TimeSource.OFFICIAL -> R.string.source_official
        TimeSource.CALCULATED -> R.string.source_calculated
        TimeSource.NEARBY_MOSQUE -> R.string.source_mosque
    },
)

@Composable
internal fun TimeSource.description(): String = stringResource(
    when (this) {
        TimeSource.OFFICIAL -> R.string.source_official_desc
        TimeSource.CALCULATED -> R.string.source_calculated_desc
        TimeSource.NEARBY_MOSQUE -> R.string.source_mosque_desc
    },
)

@Composable
internal fun OfficialArea.label(): String = stringResource(
    when (this) {
        OfficialArea.AL_AIN -> R.string.area_al_ain
        OfficialArea.DUBAI -> R.string.area_dubai
    },
)

/** Only the two modes the app actually offers; the rest are future work. */
internal val selectableIqamaModes: List<IqamaMode> =
    listOf(IqamaMode.FIXED_OFFSET, IqamaMode.FROM_MOSQUE)

@Composable
internal fun IqamaMode.label(): String = stringResource(
    when (this) {
        IqamaMode.FROM_MOSQUE -> R.string.iqama_mode_mosque
        else -> R.string.iqama_mode_fixed
    },
)

@Composable
internal fun IqamaMode.description(): String = stringResource(
    when (this) {
        IqamaMode.FROM_MOSQUE -> R.string.iqama_mode_mosque_desc
        else -> R.string.iqama_mode_fixed_desc
    },
)
