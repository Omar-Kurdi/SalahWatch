package com.salahwatch.wear

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formatters bound to the language actually on screen.
 *
 * These were top-level `val`s built from `Locale.getDefault()`. A top-level `val` is initialised
 * once per process, so after switching to Arabic and recreating the activity they carried on
 * printing the previous language\'s month names and AM/PM markers beside labels that had all
 * changed — the same bug the phone\'s `Formats` class was written to avoid, still present here.
 *
 * Every one of them is built on [withLatinDigits]. Arabic\'s CLDR numbering system is `arab`, so
 * under an `ar` locale these would otherwise emit ٥:٤٥ rather than 5:45 — glyphs the monospace face
 * does not carry, which fall back to a face that is optically larger and proportional. On a watch
 * that is the difference between a schedule whose columns line up and one whose rows overflow.
 */
internal class WatchFormats(locale: Locale) {
    private val numeric: Locale = locale.withLatinDigits()

    val time: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", numeric)
    val date: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", numeric)
    val hijri: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", numeric)
}

@Composable
internal fun watchFormats(): WatchFormats {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { WatchFormats(locale) }
}

/** See the phone\'s `Formatting.kt`; the reasoning is identical. */
internal fun Locale.withLatinDigits(): Locale =
    Locale.Builder().setLocale(this).setUnicodeLocaleKeyword("nu", "latn").build()

/**
 * A resource lookup whose numeric arguments stay in European digits.
 *
 * `getString(id, args)` formats through the configuration locale, so `%d` comes out as ٥ under
 * Arabic even though the surrounding words are fine.
 */
@Composable
internal fun numericStringResource(@StringRes id: Int, vararg args: Any): String {
    // LocalResources rather than LocalContext: reads of the latter are not invalidated when the
    // configuration changes, so after a language switch this would keep returning the previous
    // language's string. Lint catches exactly this.
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    return String.format(locale.withLatinDigits(), resources.getString(id), *args)
}
