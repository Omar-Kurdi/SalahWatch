package com.salahwatch.app

import com.salahwatch.core.model.AppLanguage
import com.salahwatch.core.model.CalculationSettings
import com.salahwatch.core.model.IqamaSettings
import com.salahwatch.core.model.MosqueRef
import com.salahwatch.core.model.OfficialArea
import com.salahwatch.core.model.PrayerAdjustments
import com.salahwatch.core.model.ThemeMode
import com.salahwatch.core.model.TimeSource
import com.salahwatch.core.preferences.PreferencesRepository
import com.salahwatch.data.location.DeviceLocationProvider
import com.salahwatch.data.location.PlaceLookup
import com.salahwatch.data.prayersync.MawaqitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Every change the settings screen can make.
 *
 * Separated from [PrayerViewModel] because reading state and changing it are different jobs, and
 * keeping them together left one class doing both for a dozen unrelated settings.
 *
 * Each command knows whether it changes the *times* or only their presentation, and refetches only
 * in the former case: switching to Arabic or to a light theme should not spend a network request.
 */
public class PrayerSettingsEditor(
    private val scope: CoroutineScope,
    private val preferences: PreferencesRepository,
    private val locationProvider: DeviceLocationProvider,
    private val placeLookup: PlaceLookup,
    private val mawaqit: MawaqitClient,
    private val onSettingsChanged: (refetch: Boolean) -> Unit,
) {
    /** Finding mosques near the configured location. Its own concern, so its own object. */
    public val mosqueFinder: MosqueFinder = MosqueFinder(scope, preferences, mawaqit)

    /** Setting the location. Likewise its own concern, and the only one needing the geocoder. */
    public val location: LocationEditor = LocationEditor(
        scope = scope,
        preferences = preferences,
        locationProvider = locationProvider,
        placeLookup = placeLookup,
        onChanged = { onSettingsChanged(true) },
    )

    public fun calculation(transform: (CalculationSettings) -> CalculationSettings) {
        apply(refetch = true) { it.copy(calculation = transform(it.calculation)) }
    }

    /**
     * Per-prayer corrections, which are applied when times are read rather than when they are
     * fetched — so unlike the rest of [calculation] this needs no refetch.
     *
     * That distinction is the whole reason this is a separate command. Routing corrections through
     * [calculation] made every tap on a stepper queue a fetch of the entire year, and the screen
     * showed nothing until it landed.
     */
    public fun adjustments(transform: (PrayerAdjustments) -> PrayerAdjustments) {
        apply(refetch = false) {
            it.copy(calculation = it.calculation.copy(adjustments = transform(it.calculation.adjustments)))
        }
    }

    /** Iqama offsets are derived on device, so this needs a recompute but never a refetch. */
    public fun iqama(transform: (IqamaSettings) -> IqamaSettings) {
        apply(refetch = false) { it.copy(iqama = transform(it.iqama)) }
    }

    public fun theme(mode: ThemeMode) = apply(refetch = false) { it.copy(theme = mode) }

    public fun language(language: AppLanguage) = apply(refetch = false) { it.copy(language = language) }

    public fun timeSource(source: TimeSource) = apply(refetch = true) { it.copy(timeSource = source) }

    public fun mosque(mosque: MosqueRef) = apply(refetch = true) {
        it.copy(mosque = mosque, timeSource = TimeSource.NEARBY_MOSQUE)
    }

    /** Picking an area also selects the source it belongs to, as picking a mosque does. */
    public fun officialArea(area: OfficialArea) = apply(refetch = true) {
        it.copy(officialArea = area, timeSource = TimeSource.OFFICIAL)
    }

    private fun apply(
        refetch: Boolean,
        transform: (com.salahwatch.core.preferences.UserPreferences) ->
        com.salahwatch.core.preferences.UserPreferences,
    ) {
        scope.launch {
            preferences.update(transform)
            onSettingsChanged(refetch)
        }
    }
}
