package com.salahwatch.core.preferences

import com.salahwatch.core.model.AppLanguage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * The regression test for a bug in the fix for another bug.
 *
 * Applying the language used to be driven by the view model's `settings` StateFlow, which has to be
 * seeded and can only be seeded with `UserPreferences()` — whose language is
 * [AppLanguage.SYSTEM]. Nothing distinguishes that placeholder from a user who genuinely chose
 * "follow system", so on every cold launch the placeholder arrived first, the stored tag was
 * cleared, the activity recreated into the device's language, and only then did the real value turn
 * up and recreate it again. Two extra recreations and a visible flash of the wrong language — on the
 * one code path whose entire job was to make Arabic work.
 *
 * The property that prevents it is that the flow must not speak before the store does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LanguageChangesTest {

    @Test
    fun `says nothing until the stored preferences arrive`() = runTest {
        val stored = MutableSharedFlow<UserPreferences>()
        val seen = mutableListOf<AppLanguage>()

        val collector = launch { stored.languageChanges().collect { seen += it } }
        runCurrent()

        assertTrue(
            seen.isEmpty(),
            "Emitting before the store has been read is what cleared the user's chosen language",
        )

        stored.emit(UserPreferences(language = AppLanguage.ARABIC))
        runCurrent()

        assertEquals(listOf(AppLanguage.ARABIC), seen)
        collector.cancel()
    }

    @Test
    fun `reports a real choice of follow-system`() = runTest {
        // The placeholder and a deliberate choice look identical; the difference is only ever
        // *when* they arrive. Once the store has spoken, SYSTEM must be honoured like any other.
        val result = flowOf(UserPreferences(language = AppLanguage.SYSTEM)).languageChanges().toList()

        assertEquals(listOf(AppLanguage.SYSTEM), result)
    }

    @Test
    fun `ignores changes to everything else`() = runTest {
        // Preferences re-emit on every edit and every revision bump. Recreating the activity
        // because an Iqama offset moved would be a visible flicker for no reason.
        val result = flowOf(
            UserPreferences(language = AppLanguage.ARABIC),
            UserPreferences(language = AppLanguage.ARABIC, revision = 1),
            UserPreferences(language = AppLanguage.ARABIC, revision = 2),
            UserPreferences(language = AppLanguage.ENGLISH, revision = 3),
        ).languageChanges().toList()

        assertEquals(listOf(AppLanguage.ARABIC, AppLanguage.ENGLISH), result)
    }
}
