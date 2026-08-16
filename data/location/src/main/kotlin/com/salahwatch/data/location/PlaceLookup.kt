package com.salahwatch.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Converts between place names and coordinates using Android's own [Geocoder].
 *
 * This replaces the geocoding the AlAdhan city endpoints used to do through 7x.ax, which shut down
 * on 2026-09-01. Using the platform is strictly better than swapping in another third-party service:
 *
 * - **No API key, no account, no service to outlive us.**
 * - **No permission.** Verified against the AOSP source: not one method on [Geocoder] carries a
 *   `@RequiresPermission` annotation. That matters because SPEC.md asks us to avoid requesting
 *   location permissions unnecessarily — a user who types their city never grants anything.
 * - **Better privacy.** The lookup goes through the OS's geocoding backend rather than a service we
 *   chose, so no location data leaves via our code. SPEC.md asks for exactly that.
 *
 * Geocoding runs on the phone; the watch never needs it.
 */
public class PlaceLookup(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Whether this device has a geocoding backend at all. False on de-Googled builds. */
    public fun isAvailable(): Boolean = Geocoder.isPresent()

    /**
     * Coordinates for a place name, or null if it cannot be resolved.
     *
     * Returns null rather than throwing on a miss: "no such place" is an ordinary outcome of a user
     * typing, not an error condition.
     */
    public suspend fun coordinatesFor(query: String): Place? {
        if (query.isBlank() || !isAvailable()) return null
        return firstAddress { geocoder, listener ->
            geocoder.getFromLocationNameCompat(query, listener)
        }?.toPlace()
    }

    /** A human-readable label for coordinates, e.g. "Manchester, United Kingdom". */
    public suspend fun describe(latitude: Double, longitude: Double): String? {
        if (!isAvailable()) return null
        return firstAddress { geocoder, listener ->
            geocoder.getFromLocationCompat(latitude, longitude, listener)
        }?.label()
    }

    private suspend fun firstAddress(
        request: (Geocoder, (List<Address>) -> Unit) -> Unit,
    ): Address? = withContext(io) {
        val geocoder = Geocoder(context)
        suspendCancellableCoroutine { continuation ->
            try {
                request(geocoder) { addresses -> continuation.resume(addresses.firstOrNull()) }
            } catch (e: IOException) {
                // The backend is unreachable. Manual coordinate entry still works, so a null here
                // degrades the UI rather than blocking the user.
                Log.d(TAG, "Geocoder unavailable; falling back to manual entry", e)
                continuation.resume(null)
            }
        }
    }

    /**
     * The asynchronous overloads arrived in API 33; below that the blocking ones are all there is.
     *
     * Both paths already run on [io], so the difference is only whether the platform hands results
     * back on a callback. minSdk for the phone app is 29, so the older branch is still reachable.
     */
    @Suppress("DEPRECATION")
    private fun Geocoder.getFromLocationNameCompat(
        query: String,
        onResult: (List<Address>) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getFromLocationName(query, MAX_RESULTS) { onResult(it) }
        } else {
            onResult(getFromLocationName(query, MAX_RESULTS).orEmpty())
        }
    }

    @Suppress("DEPRECATION")
    private fun Geocoder.getFromLocationCompat(
        latitude: Double,
        longitude: Double,
        onResult: (List<Address>) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getFromLocation(latitude, longitude, MAX_RESULTS) { onResult(it) }
        } else {
            onResult(getFromLocation(latitude, longitude, MAX_RESULTS).orEmpty())
        }
    }

    private companion object {
        const val TAG = "PlaceLookup"
        const val MAX_RESULTS = 1
    }
}

/** A resolved place: coordinates plus how to describe them. */
public data class Place(
    val latitude: Double,
    val longitude: Double,
    val label: String,
)

private fun Address.toPlace() = Place(latitude, longitude, label())

/** "City, Country", falling back through progressively coarser fields. */
private fun Address.label(): String = listOfNotNull(
    locality ?: subAdminArea ?: adminArea,
    countryName,
).joinToString(", ").ifBlank { featureName.orEmpty() }
