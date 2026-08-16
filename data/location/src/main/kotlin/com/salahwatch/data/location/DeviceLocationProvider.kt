package com.salahwatch.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.salahwatch.core.model.PrayerLocation
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Obtains the device's current position, when the user has chosen Automatic location.
 *
 * Three deliberate restraints, all following from SPEC.md's battery and privacy sections:
 *
 * - **Coarse accuracy.** Prayer times shift by under a minute across several kilometres, so
 *   [Priority.PRIORITY_BALANCED_POWER_ACCURACY] is not a compromise — fine accuracy would cost GPS
 *   power to compute an identical answer.
 * - **No background location.** Only `ACCESS_COARSE_LOCATION`, requested in the foreground. The
 *   background permission is never declared, so the app cannot track anyone.
 * - **One fix, on demand.** There is no continuous request and no location callback. A position is
 *   read when the user asks for one or when a sync notices it has none, then cached.
 */
public class DeviceLocationProvider(private val context: Context) {

    /** Whether the user has granted at least coarse location. */
    public fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** True if this device can produce a position at all — a watch without GPS may not. */
    public fun hasLocationHardware(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION)

    /**
     * A single current position, or null if unavailable.
     *
     * Null covers every ordinary reason a fix might not arrive — permission not granted, location
     * services off, no hardware, or a watch out of range of its phone. Callers fall back to the last
     * known position rather than treating this as an error.
     */
    @Suppress("ReturnCount")
    public suspend fun current(): PrayerLocation? {
        if (!hasPermission() || !hasLocationHardware()) return null

        val client = LocationServices.getFusedLocationProviderClient(context)
        val cancellation = CancellationTokenSource()

        return try {
            val location = client.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellation.token,
            ).await()
                ?: client.lastLocation.await()
                ?: return null

            PrayerLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                zone = ZoneId.systemDefault(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            // Permission was revoked between the check above and the call.
            Log.i(TAG, "Location permission withdrawn mid-request", e)
            null
        } finally {
            cancellation.cancel()
        }
    }

    public companion object {
        private const val TAG = "DeviceLocation"

        /**
         * The permission to request.
         *
         * Coarse only. Requesting `ACCESS_FINE_LOCATION` would show the user a "precise location"
         * prompt for accuracy that changes no prayer time.
         */
        public const val REQUIRED_PERMISSION: String = Manifest.permission.ACCESS_COARSE_LOCATION

        /** Whether this platform shows the approximate/precise choice — Android 12 and above. */
        public val showsPrecisionChoice: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}
