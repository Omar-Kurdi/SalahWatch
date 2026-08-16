package com.salahwatch.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salahwatch.core.calculation.PrayerOccurrence
import com.salahwatch.core.database.ScheduleStore
import com.salahwatch.core.model.effectiveLocation
import com.salahwatch.core.model.AppLanguage
import com.salahwatch.core.preferences.PreferencesRepository
import com.salahwatch.core.preferences.UserPreferences
import com.salahwatch.core.preferences.languageChanges
import com.salahwatch.data.location.DeviceLocationProvider
import com.salahwatch.data.location.PlaceLookup
import com.salahwatch.data.prayersync.MawaqitClient
import com.salahwatch.data.prayersync.PrayerSyncScheduler
import com.salahwatch.data.prayertimes.PrayerScheduleRepository
import com.salahwatch.data.sync.PrayerDataLayerPublisher
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the home screen shows. */
public sealed interface HomeState {
    public data object Loading : HomeState

    /** No location yet — the user must choose one before anything can be calculated. */
    public data object NeedsLocation : HomeState

    public data class Ready(
        val today: List<PrayerOccurrence>,
        val next: PrayerOccurrence?,
        val locationLabel: String?,
        val isEstimated: Boolean,
    ) : HomeState
}

/**
 * Drives the phone app.
 *
 * Deliberately built by hand rather than injected: the object graph is a handful of repositories
 * with no variants, so a DI container would add a build step and indirection without removing any
 * decision. If the graph grows conditional, that trade changes.
 */
public class PrayerViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = PreferencesRepository(application)
    private val repository = PrayerScheduleRepository(
        dao = ScheduleStore.dao(application),
        preferences = preferences,
    )
    private val locationProvider = DeviceLocationProvider(application)
    private val placeLookup = PlaceLookup(application)
    private val publisher = PrayerDataLayerPublisher(application)

    private val refreshTrigger = MutableStateFlow(0)

    private val _home = MutableStateFlow<HomeState>(HomeState.Loading)
    public val home: StateFlow<HomeState> = _home.asStateFlow()

    public val settings: StateFlow<UserPreferences> = preferences.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UserPreferences())

    /**
     * The chosen language, emitted only once it has actually been read.
     *
     * Separate from [settings] because that one is seeded with defaults, and a seeded default is
     * indistinguishable from a real choice of "follow system". See [languageChanges].
     */
    public val storedLanguage: Flow<AppLanguage> = preferences.preferences.languageChanges()

    init {
        // Three things can change what the screen should show, and all three have to be watched.
        //
        // Settings, so an Iqama offset or a correction is reflected immediately without a round
        // trip through the database. A manual refresh. And the cache itself — because a sync runs
        // in a worker, and without observing its result the screen kept showing whatever was cached
        // when it last reloaded. That was an off-by-one the user could see: each settings change
        // displayed the *previous* change's fetched times.
        viewModelScope.launch {
            combine(
                preferences.preferences,
                refreshTrigger,
                repository.scheduleChanges(),
            ) { prefs, _, _ -> prefs }
                .collect { prefs -> loadHome(prefs) }
        }
        PrayerSyncScheduler.ensureScheduled(application)
    }

    private suspend fun loadHome(prefs: UserPreferences) {
        val location = prefs.location.effectiveLocation
        if (location == null) {
            _home.value = HomeState.NeedsLocation
            return
        }

        val today = LocalDate.now()
        val calendar = repository.timelineCalendar(today)
        val next = calendar.nextPrayer(Instant.now())
        val todaysPrayers = calendar.occurrences.filter { it.date == today }

        _home.value = HomeState.Ready(
            today = todaysPrayers,
            next = next,
            locationLabel = location.label,
            isEstimated = todaysPrayers.any { it.source.isEstimate },
        )

        publish(prefs)
    }

    /** Pushes the current schedule to the watch. Silent when no watch is paired. */
    private suspend fun publish(prefs: UserPreferences) {
        val schedules = repository.daySchedules(LocalDate.now(), PUBLISH_DAYS)
        if (schedules.isNotEmpty()) {
            publisher.publish(schedules = schedules, preferences = prefs)
        }
    }

    /** Commands that change settings. Kept apart from the state this class exposes. */
    public val edit: PrayerSettingsEditor = PrayerSettingsEditor(
        scope = viewModelScope,
        preferences = preferences,
        locationProvider = locationProvider,
        placeLookup = placeLookup,
        mawaqit = MawaqitClient(),
        onSettingsChanged = { refetch -> onSettingsChanged(refetch) },
    )

    /** Recomputes now, and refetches too when the change alters the times themselves. */
    private fun onSettingsChanged(refetch: Boolean) {
        if (refetch) PrayerSyncScheduler.syncNow(getApplication())
    }

    public fun refresh() {
        refreshTrigger.value += 1
        PrayerSyncScheduler.syncNow(getApplication())
    }

    /**
     * What this device can actually offer.
     *
     * One value rather than a question per capability, so a screen deciding what to render asks
     * once instead of interrogating the view model field by field.
     */
    public val capabilities: Capabilities
        get() = Capabilities(
            canUseDeviceLocation = locationProvider.hasPermission(),
            canLookUpCities = placeLookup.isAvailable(),
        )

    /** Device capabilities the settings screen adapts to. */
    public data class Capabilities(
        val canUseDeviceLocation: Boolean,
        val canLookUpCities: Boolean,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** Days pushed to the watch. Matches the publisher's own cap. */
        const val PUBLISH_DAYS = PrayerDataLayerPublisher.MAX_SYNC_DAYS
    }
}
