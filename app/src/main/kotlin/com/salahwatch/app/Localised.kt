package com.salahwatch.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.salahwatch.core.model.Prayer
import com.salahwatch.core.model.School
import com.salahwatch.core.model.Shafaq

/**
 * Display names for domain types.
 *
 * The domain deliberately knows nothing about language: [Prayer.shortName] is an ASCII identifier
 * sized for the complication text budget, not something to show a reader. Names live here, in
 * resources, so Arabic is a translation rather than a second code path.
 */
@Composable
internal fun Prayer.label(): String = stringResource(
    when (this) {
        Prayer.FAJR -> R.string.prayer_fajr
        Prayer.SUNRISE -> R.string.prayer_sunrise
        Prayer.DHUHR -> R.string.prayer_dhuhr
        Prayer.ASR -> R.string.prayer_asr
        Prayer.MAGHRIB -> R.string.prayer_maghrib
        Prayer.ISHA -> R.string.prayer_isha
    },
)

@Composable
internal fun School.label(): String = stringResource(
    when (this) {
        School.SHAFI -> R.string.school_shafi
        School.HANAFI -> R.string.school_hanafi
    },
)

@Composable
internal fun School.description(): String = stringResource(
    when (this) {
        School.SHAFI -> R.string.school_shafi_desc
        School.HANAFI -> R.string.school_hanafi_desc
    },
)

@Composable
internal fun Shafaq.label(): String = stringResource(
    when (this) {
        Shafaq.GENERAL -> R.string.shafaq_general
        Shafaq.AHMER -> R.string.shafaq_ahmer
        Shafaq.ABYAD -> R.string.shafaq_abyad
    },
)

@Composable
internal fun Shafaq.description(): String = stringResource(
    when (this) {
        Shafaq.GENERAL -> R.string.shafaq_general_desc
        Shafaq.AHMER -> R.string.shafaq_ahmer_desc
        Shafaq.ABYAD -> R.string.shafaq_abyad_desc
    },
)
