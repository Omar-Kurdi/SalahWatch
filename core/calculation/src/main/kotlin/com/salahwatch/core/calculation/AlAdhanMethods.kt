package com.salahwatch.core.calculation

import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.CalculationParameters
import com.batoulapps.adhan2.HighLatitudeRule
import com.batoulapps.adhan2.Madhab
import com.batoulapps.adhan2.PrayerAdjustments as AdhanAdjustments
import com.batoulapps.adhan2.model.Shafaq as AdhanShafaq
import com.salahwatch.core.model.CalculationSettings
import com.salahwatch.core.model.School
import com.salahwatch.core.model.Shafaq

/**
 * One AlAdhan calculation method, expressed in the terms adhan2 needs.
 *
 * @param fajrAngle solar depression angle for Fajr.
 * @param ishaAngle solar depression angle for Isha, or null when the method uses a fixed interval.
 * @param ishaIntervalMinutes minutes after Maghrib for Isha, used instead of [ishaAngle].
 * @param dhuhrOffset minutes AlAdhan adds to Dhuhr for this method.
 * @param maghribOffset minutes AlAdhan adds to Maghrib for this method.
 */
public data class MethodParameters(
    val id: Int,
    val name: String,
    val fajrAngle: Double,
    val ishaAngle: Double? = null,
    val ishaIntervalMinutes: Int = 0,
    val dhuhrOffset: Int = 0,
    val maghribOffset: Int = 0,
)

/**
 * The AlAdhan calculation methods, transcribed from the live `GET /v1/methods` response.
 *
 * These exist so the offline fallback reproduces the *same* method the online path uses. adhan2's
 * own `CalculationMethod` presets are deliberately **not** used for the angles: where the two
 * disagree, AlAdhan is authoritative here, because AlAdhan is what produces the cached times the
 * user actually sees. The clearest example is Dubai — adhan2's `DUBAI` preset applies
 * `sunrise -3, dhuhr +3, asr +3, maghrib +3`, whereas AlAdhan method 16 reports offsets of
 * `Dhuhr +3, Maghrib +3` and leaves Asr and Sunrise alone. Inheriting adhan2's preset would put the
 * offline estimate three minutes off the online value on Asr, every single day.
 *
 * Method 99 ("None"/custom) is intentionally absent: it carries no parameters of its own.
 */
public object AlAdhanMethods {

    // A transcription of published reference data, where the literals *are* the meaning: naming
    // 18.2 as a constant would only restate the field it is already assigned to. Each row is
    // verifiable against `GET https://api.aladhan.com/v1/methods`.
    @Suppress("MagicNumber")
    private val byId: Map<Int, MethodParameters> = listOf(
        MethodParameters(0, "Shia Ithna-Ashari, Leva Institute, Qum", 16.0, 14.0),
        MethodParameters(1, "University of Islamic Sciences, Karachi", 18.0, 18.0),
        MethodParameters(2, "Islamic Society of North America (ISNA)", 15.0, 15.0),
        MethodParameters(3, "Muslim World League", 18.0, 17.0),
        MethodParameters(4, "Umm Al-Qura University, Makkah", 18.5, ishaIntervalMinutes = 90),
        MethodParameters(5, "Egyptian General Authority of Survey", 19.5, 17.5),
        MethodParameters(7, "Institute of Geophysics, University of Tehran", 17.7, 14.0),
        MethodParameters(8, "Gulf Region", 19.5, ishaIntervalMinutes = 90),
        MethodParameters(9, "Kuwait", 18.0, 17.5),
        MethodParameters(10, "Qatar", 18.0, ishaIntervalMinutes = 90),
        MethodParameters(11, "Majlis Ugama Islam Singapura, Singapore", 20.0, 18.0),
        MethodParameters(12, "Union Organization Islamic de France", 12.0, 12.0),
        MethodParameters(13, "Diyanet İşleri Başkanlığı, Turkey", 18.0, 17.0),
        MethodParameters(14, "Spiritual Administration of Muslims of Russia", 16.0, 15.0),
        MethodParameters(MOONSIGHTING_ID, "Moonsighting Committee Worldwide", 18.0, 18.0),
        MethodParameters(DUBAI_ID, "Dubai", 18.2, 18.2, dhuhrOffset = 3, maghribOffset = 3),
        MethodParameters(17, "Jabatan Kemajuan Islam Malaysia (JAKIM)", 20.0, 18.0),
        MethodParameters(18, "Tunisia", 18.0, 18.0),
        MethodParameters(19, "Algeria", 18.0, 17.0),
        MethodParameters(20, "Kementerian Agama Republik Indonesia", 20.0, 18.0),
        MethodParameters(21, "Morocco", 19.0, 17.0),
        MethodParameters(22, "Comunidade Islamica de Lisboa", 18.0, ishaIntervalMinutes = 77, maghribOffset = 3),
        MethodParameters(23, "Ministry of Awqaf, Jordan", 18.0, 18.0, maghribOffset = 5),
    ).associateBy { it.id }

    /** Every supported method, ordered by id, for the settings screen. */
    public val all: List<MethodParameters> = byId.values.sortedBy { it.id }

    /** Parameters for [id], or null if the id is not a method this app supports. */
    public operator fun get(id: Int): MethodParameters? = byId[id]

    /**
     * Translates app settings into adhan2 calculation parameters.
     *
     * Falls back to the Dubai method when [CalculationSettings.methodId] is unrecognised, matching
     * the app's default rather than failing — an unknown id should degrade to a sensible schedule,
     * not to no schedule at all.
     */
    public fun parametersFor(settings: CalculationSettings): CalculationParameters {
        val method = byId[settings.methodId] ?: byId.getValue(DUBAI_ID)

        return CalculationParameters(
            fajrAngle = method.fajrAngle,
            ishaAngle = method.ishaAngle ?: 0.0,
            ishaInterval = method.ishaIntervalMinutes,
            // Only Moonsighting carries behaviour beyond its angles (seasonal twilight adjustment
            // and the >55° latitude rule), so it is the one method identified to adhan2 by name.
            method = if (method.id == MOONSIGHTING_ID) {
                CalculationMethod.MOON_SIGHTING_COMMITTEE
            } else {
                CalculationMethod.OTHER
            },
            madhab = when (settings.school) {
                School.SHAFI -> Madhab.SHAFI
                School.HANAFI -> Madhab.HANAFI
            },
            // AlAdhan reports `latitudeAdjustmentMethod: ANGLE_BASED` on every response, which is
            // adhan2's TWILIGHT_ANGLE (night portions of fajrAngle/60 and ishaAngle/60). adhan2
            // otherwise picks a rule from the latitude itself, which diverges badly where it matters
            // most: measured against the golden fixtures, London in late June was 71 minutes out on
            // Fajr before this was set. Setting it explicitly is what makes the offline fallback
            // usable at high latitudes at all.
            highLatitudeRule = HighLatitudeRule.TWILIGHT_ANGLE,
            methodAdjustments = AdhanAdjustments(
                dhuhr = method.dhuhrOffset,
                maghrib = method.maghribOffset,
            ),
            // The user's own corrections are deliberately absent. They are applied once, at read
            // time, by com.salahwatch.core.model.shiftedBy — so that they reach mosque
            // times as well as calculated ones. Applying them here too would double them.
            shafaq = when (settings.shafaq) {
                Shafaq.GENERAL -> AdhanShafaq.GENERAL
                Shafaq.AHMER -> AdhanShafaq.AHMER
                Shafaq.ABYAD -> AdhanShafaq.ABYAD
            },
        )
    }

    private const val MOONSIGHTING_ID = 15
    private const val DUBAI_ID = 16
}
