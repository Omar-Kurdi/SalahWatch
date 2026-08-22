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
import com.salahwatch.app.widget.PrayerWidgetProvider
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
        /** The day being shown, which is not necessarily today. */
        val date: LocalDate,
        val prayers: List<PrayerOccurrence>,
        /**
         * The next prayer from now, always relative to the real today.
         *
         * Kept even while another date is shown so the caller keeps a zone to format with, but
         * only rendered as a countdown when [isToday].
         */
        val next: PrayerOccurrence?,
        val locationLabel: String?,
        val isEstimated: Boolean,
        /**
         * Whether [date] was today *when this state was built*.
         *
         * Resolved once rather than computed from `LocalDate.now()` on each read. A getter flips at
         * midnight while [date] does not, so an app left open overnight would drop the clock, the
         * day arc and the next-prayer panel while the date strip still read "Today" — a screen
         * contradicting itself. This is the same trap `scheduleChanges()` was rewritten to avoid.
         */
        val isToday: Boolean,
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

    private val _selectedDate = MutableStateFlow(LocalDate.now())

    /** The day the Today screen is showing. Defaults to today and returns there on [refresh]. */
    public val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

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
                _selectedDate,
            ) { prefs, _, _, date -> prefs to date }
                .collect { (prefs, date) -> loadHome(prefs, date) }
        }
        PrayerSyncScheduler.ensureScheduled(application)
    }

    private suspend fun loadHome(prefs: UserPreferences, selected: LocalDate) {
        val location = prefs.location.effectiveLocation
        if (location == null) {
            _home.value = HomeState.NeedsLocation
            return
        }

        val today = LocalDate.now()

        // Two calendars, deliberately. "What is next" is always a question about *now*, and only
        // the timeline calendar can answer it across midnight — it starts a day early so a late
        // Isha still resolves. The day being browsed is a separate, narrower question, and asking
        // the timeline for a date a month out would filter to nothing.
        val timeline = repository.timelineCalendar(today)
        val next = timeline.nextPrayer(Instant.now())
        val browsed = if (selected == today) timeline else repository.calendar(selected, 1)
        val prayers = browsed.occurrences.filter { it.date == selected }

        _home.value = HomeState.Ready(
            date = selected,
            isToday = selected == today,
            prayers = prayers,
            next = next,
            locationLabel = location.label,
            isEstimated = prayers.any { it.source.isEstimate },
        )

        // Only when today is on screen. Browsing to another date must not cost a Data Layer push
        // to the watch on every arrow tap — the watch always wants today, whatever the phone shows.
        // Both of these are for today and only today. The widget always shows today, so browsing
        // to another date can never change what it displays — and without the gate, stepping back
        // a month would fire thirty broadcasts, each one a database read and an alarm re-arm.
        if (selected == today) {
            publish(prefs)

            // The widget reads the same cache but observes nothing, so a settings change made here
            // — an Iqama offset, a correction — would not reach it until its next scheduled redraw.
            PrayerWidgetProvider.refresh(getApplication())
        }
    }

    /** Shows [date], clamped to the range the picker offers. */
    public fun selectDate(date: LocalDate) {
        val today = LocalDate.now()
        _selectedDate.value = date.coerceIn(today.minusMonths(BROWSE_MONTHS), today.plusMonths(BROWSE_MONTHS))
    }

    /** Steps one day either way. [days] is signed. */
    public fun stepDate(days: Long) {
        selectDate(_selectedDate.value.plusDays(days))
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
        // Refreshing is "show me where I am now", so it also returns from any browsed date.
        _selectedDate.value = LocalDate.now()
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

    public companion object {
        /**
         * How far either way the date picker reaches.
         *
         * Past days come from the cache — [com.salahwatch.data.prayersync.PrayerSyncer] retains at
         * least this much history so browsing back shows the times that were actually published,
         * not a fresh estimate of them.
         */
        public const val BROWSE_MONTHS: Long = 1

        private const val STOP_TIMEOUT_MS = 5_000L

        /** Days pushed to the watch. Matches the publisher's own cap. */
        private const val PUBLISH_DAYS = PrayerDataLayerPublisher.MAX_SYNC_DAYS
    }
}
