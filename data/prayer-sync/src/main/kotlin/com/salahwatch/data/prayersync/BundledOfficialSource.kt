package com.salahwatch.data.prayersync

import android.content.Context
import android.util.Log
import com.salahwatch.core.model.DaySchedule
import com.salahwatch.core.model.OfficialArea
import java.io.IOException

/**
 * Reads an area's official timetable out of the APK.
 *
 * There is no network here by design. The data is a published table that changes once a year, so
 * shipping it as an asset makes the exact times available on a fresh install, offline, on the first
 * launch — and removes any runtime dependency on an authority's website staying up or keeping its
 * shape. The cost is that coverage ends when the bundle does, which is why callers must handle
 * [calendarFor] returning fewer days than they asked for.
 */
public class BundledOfficialSource(
    private val context: Context,
) : OfficialTimeSource {

    private val cache = HashMap<OfficialArea, List<DaySchedule>>()

    // Each exit is the same answer — no usable bundle, fall back — reached from a different cause.
    @Suppress("ReturnCount")
    override suspend fun calendarFor(area: OfficialArea): List<DaySchedule>? {
        cache[area]?.let { return it }

        val text = try {
            context.assets.open("$ASSET_DIR/${area.slug}.json").use { it.readBytes().decodeToString() }
        } catch (e: IOException) {
            // Only reachable if the asset is missing from the build, so it is a packaging fault
            // rather than anything the user did.
            Log.e(TAG, "No bundled timetable for ${area.slug}", e)
            return null
        }

        val parsed = OfficialTimetableParser.parse(text)
        if (parsed == null) {
            Log.e(TAG, "Bundled timetable for ${area.slug} failed validation")
            return null
        }

        cache[area] = parsed
        return parsed
    }

    private companion object {
        const val ASSET_DIR = "official"
        const val TAG = "BundledOfficialSource"
    }
}
