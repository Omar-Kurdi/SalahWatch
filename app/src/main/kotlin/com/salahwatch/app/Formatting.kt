package com.salahwatch.app

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TIME_PATTERN = "h:mm a"
private const val CLOCK_PATTERN = "h:mm:ss a"
private const val GREGORIAN_PATTERN = "EEEE d MMMM yyyy"
private const val HIJRI_PATTERN = "d MMMM yyyy"
private const val DATE_PATTERN = "EEE d MMM"

/**
 * Formatters bound to the language the app is currently showing.
 *
 * Resolved from the context rather than from `Locale.getDefault()` at class-load time. Top-level
 * formatter constants are initialised once per process, so after switching to Arabic and recreating
 * the activity they would still be printing the previous language's month names and AM/PM markers
 * next to labels that had all changed.
 *
 * Every formatter here is built on [withLatinDigits] — see that function for why.
 */
internal class Formats(locale: Locale) {
    /** The language as chosen, for anything that is words rather than figures. */
    val locale: Locale = locale

    private val numeric: Locale = locale.withLatinDigits()

    val time: DateTimeFormatter = DateTimeFormatter.ofPattern(TIME_PATTERN, numeric)
    val clock: DateTimeFormatter = DateTimeFormatter.ofPattern(CLOCK_PATTERN, numeric)
    val gregorian: DateTimeFormatter = DateTimeFormatter.ofPattern(GREGORIAN_PATTERN, numeric)
    val hijri: DateTimeFormatter = DateTimeFormatter.ofPattern(HIJRI_PATTERN, numeric)
    val date: DateTimeFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN, numeric)
}

@Composable
internal fun formats(): Formats {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { Formats(locale) }
}

/**
 * The same language, but with European digits.
 *
 * Arabic's default numbering system in CLDR is `arab`, so an `ar` locale can render digits as
 * ٥:٤٥ rather than 5:45. **Measured**, on the JDK: `String.format("%d")` does exactly this, so
 * every countdown is affected. `DateTimeFormatter` did *not* — it produced Latin digits for `ar` —
 * but Android's CLDR data is not the desktop JDK's, and this has not been checked on a device. The
 * formatters are pinned anyway, so the output is the same either way rather than depending on which
 * platform resolves it.
 *
 * Where it does happen, two things go wrong at once:
 *
 * 1. `FontFamily.Monospace` has no Arabic-Indic coverage, so every digit falls back to the system
 *    Arabic face. That face is optically larger at the same `sp` — the most likely explanation for
 *    text appearing to jump in size the moment the language changes.
 * 2. The fallback is proportional, which quietly destroys the one typographic decision this app
 *    actually makes — that a column of times is tabular and lines up.
 *
 * Forcing `latn` fixes both without touching a single type size. Arabic month names, weekday names
 * and the AM/PM markers are unaffected: those come from the language, not the numbering system.
 */
internal fun Locale.withLatinDigits(): Locale =
    Locale.Builder().setLocale(this).setUnicodeLocaleKeyword("nu", "latn").build()

/**
 * [androidx.compose.ui.res.stringResource], for strings whose arguments are numbers.
 *
 * `Context.getString(id, args)` formats through the *configuration* locale, which under Arabic
 * means `%d` comes out as ٥ rather than 5 — the same fallback problem [withLatinDigits] describes,
 * arriving by a different route. Countdowns are the visible case: "خلال ٥ س ٣٠ د" mixes a
 * fallback-rendered numeral into a line of otherwise ordinary text, and the numerals sit taller
 * than everything around them.
 *
 * The string itself is still resolved from resources, so the Arabic translation is untouched. Only
 * the digits substituted into it are pinned.
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
