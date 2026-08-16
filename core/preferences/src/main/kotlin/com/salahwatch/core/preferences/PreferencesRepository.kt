package com.salahwatch.core.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "prayer_prefs")

/**
 * Reads and writes [UserPreferences].
 *
 * Stored as a single JSON blob under one key rather than as individual typed keys. That keeps the
 * on-disk form identical to the Data Layer payload, so phone and watch cannot drift apart in how
 * they interpret settings, and adding a field is one change instead of three.
 */
public class PreferencesRepository(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /** The current preferences, re-emitted on every change. */
    public val preferences: Flow<UserPreferences> =
        context.dataStore.data.map { it.decode() }

    /** A one-shot read, for callers that cannot collect a flow — notably complication services. */
    public suspend fun current(): UserPreferences = preferences.first()

    /**
     * Applies [transform] to the stored preferences and bumps the revision.
     *
     * The revision bump is unconditional rather than dependent on whether anything actually
     * changed: a caller that writes identical values still expects the watch to be re-notified,
     * and comparing for equality here would silently swallow that.
     */
    public suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        context.dataStore.edit { store ->
            val updated = transform(store.decode()).next()
            store[KEY] = json.encodeToString(updated)
        }
    }

    /** Overwrites everything — used by the watch when the phone sends a newer revision. */
    public suspend fun replace(preferences: UserPreferences) {
        context.dataStore.edit { store ->
            store[KEY] = json.encodeToString(preferences)
        }
    }

    /**
     * Defaults are returned when nothing is stored, and also when what is stored cannot be parsed.
     *
     * Falling back on corrupt data is deliberate: a complication that cannot read preferences must
     * still show prayer times. Defaults produce a usable schedule, whereas propagating the failure
     * would leave a blank slot on the watch face.
     */
    private fun Preferences.decode(): UserPreferences {
        val stored = this[KEY] ?: return UserPreferences()
        return try {
            json.decodeFromString<UserPreferences>(stored)
        } catch (e: SerializationException) {
            Log.w(TAG, "Stored preferences could not be parsed; using defaults", e)
            UserPreferences()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Stored preferences were malformed; using defaults", e)
            UserPreferences()
        }
    }

    private companion object {
        const val TAG = "PreferencesRepository"
        val KEY = stringPreferencesKey("user_preferences")
    }
}
