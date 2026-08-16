package com.salahwatch.core.preferences

import android.content.Context
import android.content.res.Configuration
import com.salahwatch.core.model.AppLanguage
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Makes the app's own language choice actually take effect.
 *
 * This replaces `AppCompatDelegate.setApplicationLocales`, which was in use and silently did
 * nothing. That API sources a `Context` by walking AppCompat's registry of live activity delegates;
 * with Compose-only `ComponentActivity` screens there are no delegates, so the call returned without
 * doing anything and picking Arabic changed nothing on screen. Extending `AppCompatActivity`
 * instead is not the fix either — the manifest theme is a framework `android:Theme.Material`
 * descendant, and AppCompat throws at inflation without a `Theme.AppCompat` ancestor.
 *
 * So the override is done directly: every activity re-resolves its resources against the chosen
 * locale in `attachBaseContext`, and changing the choice recreates the activity. This behaves the
 * same on every supported API level, which matters because the phone's `minSdk` is 29 and the
 * framework's own per-app language API only begins at 33.
 *
 * The tag is mirrored into [android.content.SharedPreferences] rather than read from the DataStore
 * that owns it. `attachBaseContext` runs before anything can suspend, and blocking the main thread
 * on a DataStore read at every activity launch to answer a question this small is not a trade worth
 * making. [UserPreferences.language] stays the source of truth; this is a cache of one field.
 */
public object LanguageOverride {

    /**
     * [base] with its resources resolved against the chosen language.
     *
     * Pure with respect to the rest of the process: it returns a context and changes nothing else.
     * That matters because complication services call this from a background thread to resolve
     * their own labels, and a service quietly reaching out to change process-wide state would be a
     * surprise. Activities want that extra step and ask for it explicitly — see [attach].
     *
     * Returns [base] unchanged when the choice is to follow the system, so the device's own locale
     * — and its own layout direction — apply untouched.
     */
    public fun wrap(base: Context): Context {
        val locale = storedLocale(base) ?: return base

        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(configuration)
    }

    /**
     * [wrap], and the JVM default locale set to match.
     *
     * For `Activity.attachBaseContext`. The default is what anything resolving a locale outside the
     * resource system reads — notably the date and time formatters, which would otherwise stay in
     * the device's language while every label around them changed. Process-global, so it is done
     * from an activity attaching in the foreground and nowhere else.
     */
    public fun attach(base: Context): Context {
        storedLocale(base)?.let(Locale::setDefault)
        return wrap(base)
    }

    /**
     * Records [language] as the choice, and reports whether it differs from what is in effect.
     *
     * A true result means the caller must recreate its activity: resources are resolved once at
     * attach, so nothing already on screen will change language on its own.
     */
    public fun select(context: Context, language: AppLanguage): Boolean {
        if (language.tag == storedTag(context)) return false

        // Committed rather than applied: the activity is about to be recreated and must read this
        // back on the way up.
        context.preferences()
            .edit()
            .apply { language.tag?.let { putString(KEY_TAG, it) } ?: remove(KEY_TAG) }
            .commit()

        return true
    }

    private fun storedLocale(context: Context): Locale? =
        storedTag(context)?.let(Locale::forLanguageTag)

    private fun storedTag(context: Context): String? =
        context.preferences().getString(KEY_TAG, null)

    private fun Context.preferences() =
        applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    private const val STORE = "app_language"
    private const val KEY_TAG = "language_tag"
}

/**
 * The chosen language, and only once it is actually known.
 *
 * Deliberately a cold [Flow] and not a `StateFlow`. A `StateFlow` has to be seeded, and the only
 * value available to seed it with is `UserPreferences()` — whose language is
 * [AppLanguage.SYSTEM]. That placeholder is indistinguishable from a user who really did choose
 * "follow system", so driving [LanguageOverride.select] from it made every cold launch clear the
 * stored tag, recreate into the device language, then recreate again once the real value arrived:
 * two extra recreations and a visible flash of the wrong language, on the very path that was
 * supposed to make Arabic work.
 *
 * A cold flow cannot emit before the store does, so the first value is always one the user chose.
 */
public fun Flow<UserPreferences>.languageChanges(): Flow<AppLanguage> =
    map { it.language }.distinctUntilChanged()
