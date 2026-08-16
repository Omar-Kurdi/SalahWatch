package com.salahwatch.core.preferences

import com.salahwatch.core.model.CalculationSettings
import com.salahwatch.core.model.IqamaSettings
import com.salahwatch.core.model.AppLanguage
import com.salahwatch.core.model.LocationMode
import com.salahwatch.core.model.MosqueRef
import com.salahwatch.core.model.TimeSource
import com.salahwatch.core.model.ThemeMode
import kotlinx.serialization.Serializable

/**
 * Everything the user can configure, in one serialisable object.
 *
 * Held as a single value rather than as loose keys because this is also the payload that crosses the
 * Data Layer to the watch: one object means one `DataItem`, one version to reason about, and no
 * possibility of the watch running with a half-applied settings change.
 */
@Serializable
public data class UserPreferences(
    val calculation: CalculationSettings = CalculationSettings(),
    val iqama: IqamaSettings = IqamaSettings(),
    val location: LocationMode = LocationMode.Automatic(),
    val timeSource: TimeSource = TimeSource.CALCULATED,
    val mosque: MosqueRef? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    /**
     * Bumped by the phone on every change so the watch can tell a genuine update from a
     * redelivery of the same `DataItem`.
     */
    val revision: Long = 0,
) {
    public fun next(): UserPreferences = copy(revision = revision + 1)
}
