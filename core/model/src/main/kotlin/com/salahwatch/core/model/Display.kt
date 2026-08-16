package com.salahwatch.core.model

import kotlinx.serialization.Serializable

/**
 * Which colour scheme the phone app uses.
 *
 * The watch is always dark: on an AMOLED panel a black pixel is an unlit pixel, so a light theme
 * would cost battery for no benefit on a screen that is glanced at rather than read.
 */
@Serializable
public enum class ThemeMode {
    /** Follow the device setting. */
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Which language the apps display in.
 *
 * [SYSTEM] defers to the device, which is the right default — someone whose phone is in Arabic
 * expects Arabic. The explicit options exist because a bilingual user often wants prayer names in
 * Arabic while the rest of their phone is in English, or the reverse.
 */
@Serializable
public enum class AppLanguage(public val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    ARABIC("ar"),
}
