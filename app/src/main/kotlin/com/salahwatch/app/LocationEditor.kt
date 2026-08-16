package com.salahwatch.app

import com.salahwatch.core.model.LocationMode
import com.salahwatch.core.model.PrayerLocation
import com.salahwatch.core.preferences.PreferencesRepository
import com.salahwatch.data.location.DeviceLocationProvider
import com.salahwatch.data.location.PlaceLookup
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The three ways a location can be set, kept together.
 *
 * Its own object for the same reason [MosqueFinder] is: these are the only commands that need the
 * device's location provider and the geocoder, and every one of them changes the times, so they
 * share both their dependencies and their consequence. Leaving them among the display settings put
 * unrelated things in one class and made it hard to see which changes cost a refetch.
 */
public class LocationEditor(
    private val scope: CoroutineScope,
    private val preferences: PreferencesRepository,
    private val locationProvider: DeviceLocationProvider,
    private val placeLookup: PlaceLookup,
    private val onChanged: () -> Unit,
) {
    /** Follow the device, seeded with a fix taken now so the app is usable immediately. */
    public fun automatic() {
        scope.launch {
            val fix = locationProvider.current()
            val labelled = fix?.copy(label = placeLookup.describe(fix.latitude, fix.longitude))
            preferences.update { it.copy(location = LocationMode.Automatic(last = labelled)) }
            onChanged()
        }
    }

    /** Resolves a typed place name. Returns false when nothing matched. */
    public suspend fun city(query: String): Boolean {
        val place = placeLookup.coordinatesFor(query) ?: return false
        manual(place.latitude, place.longitude, place.label)
        return true
    }

    /** A fixed point, so prayer times never shift because of a stray fix. */
    public fun manual(latitude: Double, longitude: Double, label: String?) {
        scope.launch {
            preferences.update {
                it.copy(
                    location = LocationMode.Manual(
                        PrayerLocation(latitude, longitude, ZoneId.systemDefault(), label),
                    ),
                )
            }
            onChanged()
        }
    }
}
