package com.salahwatch.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.salahwatch.core.model.ThemeMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

internal val PrayerColorScheme = darkColorScheme(
    primary = PrayerColors.Brass,
    onPrimary = PrayerColors.Night,
    primaryContainer = PrayerColors.SurfaceRaised,
    onPrimaryContainer = PrayerColors.Ink,
    secondary = PrayerColors.Zenith,
    background = PrayerColors.Night,
    onBackground = PrayerColors.Ink,
    surface = PrayerColors.Surface,
    onSurface = PrayerColors.Ink,
    surfaceVariant = PrayerColors.SurfaceRaised,
    onSurfaceVariant = PrayerColors.InkMuted,
    outline = PrayerColors.Line,
    error = PrayerColors.Warning,
)

/**
 * Type is carried by scale, weight and tracking rather than by a shipped typeface.
 *
 * The one craft decision that matters here: **times are tabular**. A prayer schedule is a column of
 * figures, and proportional digits make that column ripple. `FontFamily.Monospace` fixes the advance
 * width so the times align exactly, which is the difference between a list that reads as data and
 * one that reads as prose.
 *
 * Labels are uppercase with wide tracking; the eye reads them as structure, not as content, which
 * lets the times stay the loudest thing on screen.
 */
internal val PrayerTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Light,
        fontSize = 56.sp,
        letterSpacing = (-2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Light,
        fontSize = 40.sp,
        letterSpacing = (-1).sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = 4.sp,
    ),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    // No colour here. A colour baked into a shared TextStyle survives the theme switch, so this
    // one printed dark-theme grey onto light-theme cards; every caller passes its own from the
    // palette instead.
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        textAlign = TextAlign.Start,
    ),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.5.sp),
)

/** Times, everywhere they appear. Tabular so columns of figures line up. */
internal val TimeStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    letterSpacing = 0.sp,
)

internal val PrayerLightColorScheme = lightColorScheme(
    primary = PrayerColors.DayBrass,
    onPrimary = PrayerColors.DaySurface,
    primaryContainer = PrayerColors.DaySurfaceRaised,
    onPrimaryContainer = PrayerColors.DayInk,
    secondary = PrayerColors.Zenith,
    background = PrayerColors.DayBackground,
    onBackground = PrayerColors.DayInk,
    surface = PrayerColors.DaySurface,
    onSurface = PrayerColors.DayInk,
    surfaceVariant = PrayerColors.DaySurfaceRaised,
    onSurfaceVariant = PrayerColors.DayInkMuted,
    outline = PrayerColors.DayLine,
    error = PrayerColors.Warning,
)

/**
 * The palette in use, resolved from the chosen [ThemeMode].
 *
 * Exposed as a composition local so every screen reads the same values without each one having to
 * branch on the mode. Dark is the default when following the system, matching SPEC's direction.
 */
internal data class PrayerPalette(
    val background: androidx.compose.ui.graphics.Color,
    val surface: androidx.compose.ui.graphics.Color,
    val surfaceRaised: androidx.compose.ui.graphics.Color,
    val brass: androidx.compose.ui.graphics.Color,
    val ink: androidx.compose.ui.graphics.Color,
    val inkMuted: androidx.compose.ui.graphics.Color,
    val inkFaint: androidx.compose.ui.graphics.Color,
    val line: androidx.compose.ui.graphics.Color,
    val isDark: Boolean,
)

private val NightPalette = PrayerPalette(
    background = PrayerColors.Night,
    surface = PrayerColors.Surface,
    surfaceRaised = PrayerColors.SurfaceRaised,
    brass = PrayerColors.Brass,
    ink = PrayerColors.Ink,
    inkMuted = PrayerColors.InkMuted,
    inkFaint = PrayerColors.InkFaint,
    line = PrayerColors.Line,
    isDark = true,
)

private val DayPalette = PrayerPalette(
    background = PrayerColors.DayBackground,
    surface = PrayerColors.DaySurface,
    surfaceRaised = PrayerColors.DaySurfaceRaised,
    brass = PrayerColors.DayBrass,
    ink = PrayerColors.DayInk,
    inkMuted = PrayerColors.DayInkMuted,
    inkFaint = PrayerColors.DayInkFaint,
    line = PrayerColors.DayLine,
    isDark = false,
)

internal val LocalPalette = staticCompositionLocalOf { NightPalette }

@Composable
internal fun PrayerTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val palette = if (dark) NightPalette else DayPalette

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = if (dark) PrayerColorScheme else PrayerLightColorScheme,
            typography = PrayerTypography,
            content = content,
        )
    }
}
