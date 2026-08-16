package com.salahwatch.app

import android.util.Log
import com.salahwatch.core.model.MosqueRef
import com.salahwatch.core.model.effectiveLocation
import com.salahwatch.core.preferences.PreferencesRepository
import com.salahwatch.data.prayersync.MawaqitClient
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the mosque picker has to show. */
public sealed interface NearbyMosques {
    public data object Idle : NearbyMosques
    public data object Searching : NearbyMosques
    public data object NeedsLocation : NearbyMosques
    public data object Failed : NearbyMosques
    public data class Found(val mosques: List<MosqueRef>) : NearbyMosques
}

/**
 * Looks up mosques near the configured location.
 *
 * Each outcome is a distinct state rather than an empty list, because "still looking", "no location
 * set", "could not reach the directory" and "genuinely none nearby" need four different things said
 * to the user, and only the last is not actionable.
 */
public class MosqueFinder(
    private val scope: CoroutineScope,
    private val preferences: PreferencesRepository,
    private val mawaqit: MawaqitClient,
) {
    private val _state = MutableStateFlow<NearbyMosques>(NearbyMosques.Idle)
    public val state: StateFlow<NearbyMosques> = _state.asStateFlow()

    public fun find() {
        scope.launch {
            val location = preferences.current().location.effectiveLocation
            if (location == null) {
                _state.value = NearbyMosques.NeedsLocation
                return@launch
            }

            _state.value = NearbyMosques.Searching
            _state.value = try {
                val found = mawaqit.nearby(location)
                refreshChosenMosque(found)
                NearbyMosques.Found(found)
            } catch (e: IOException) {
                Log.w(TAG, "Could not reach the mosque directory", e)
                NearbyMosques.Failed
            }
        }
    }

    /**
     * Re-reads the chosen mosque's details from a fresh search result.
     *
     * A [MosqueRef] is stored as it looked when it was picked, so anything the directory later
     * corrects — a renamed mosque, or Iqama times this app previously failed to parse — would stay
     * wrong until the user happened to select it again. Matching on uuid rather than name, because
     * the name is the thing most likely to have changed.
     *
     * Only the mosque's own details are written, never the choice itself, and only when something
     * actually differs — so this cannot quietly move the user to a different mosque, and cannot
     * cause a write on every visit to the settings screen.
     */
    private suspend fun refreshChosenMosque(found: List<MosqueRef>) {
        val chosen = preferences.current().mosque ?: return
        val fresh = found.firstOrNull { it.id == chosen.id } ?: return

        // Distance is excluded on both sides. It moves with every GPS fix, so comparing it would
        // make this write — and re-notify the watch — on essentially every search, and nothing
        // reads the stored value anyway.
        val merged = fresh.copy(distanceMetres = chosen.distanceMetres)
        if (merged == chosen) return

        preferences.update { it.copy(mosque = merged) }
    }

    private companion object {
        const val TAG = "MosqueFinder"
    }
}
