package com.salahwatch.app

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salahwatch.app.ui.LocalPalette
import com.salahwatch.app.ui.PrayerTheme
import com.salahwatch.core.preferences.LanguageOverride
import java.time.Instant
import kotlinx.coroutines.delay

private enum class Tab(val label: Int) {
    TODAY(R.string.tab_today),
    SETTINGS(R.string.tab_settings),
}

/**
 * The phone app: where configuration happens and the day is reviewed.
 *
 * The watch is the surface for glancing; this is the one for deciding. That split is why the phone
 * owns network fetching, geocoding and location permission, and the watch owns none of them.
 */
public class MainActivity : ComponentActivity() {

    /**
     * The chosen language is applied here, before any resource is resolved.
     *
     * It has to be this early: `stringResource`, the layout direction and the date formatters all
     * read from the context the activity was attached with, so a locale applied later would leave
     * half the screen in the previous language until something happened to recreate it.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageOverride.attach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: PrayerViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            // Driven by the stored flow, never by `settings` — that one is seeded with
            // defaults, and acting on the seed cleared the stored tag and recreated into the
            // wrong language on every cold launch. Recreating is what applies the choice:
            // resources were resolved at attach, so nothing already composed can change in place.
            LaunchedEffect(Unit) {
                viewModel.storedLanguage.collect { language ->
                    if (LanguageOverride.select(this@MainActivity, language)) recreate()
                }
            }

            PrayerTheme(mode = settings.theme) {
                SystemBars()
                PrayerApp(viewModel)
            }
        }
    }
}

/**
 * Makes the window itself agree with the chosen theme.
 *
 * Two things Compose cannot do on its own. The window background is what shows during launch and
 * behind any transition, and it comes from the manifest theme — which is a single fixed value, so
 * in light mode it was a dark rectangle framing a pale app. And with edge-to-edge and transparent
 * system bars, the status bar icons are drawn by the system: left alone they stay light, which is
 * invisible against a light theme's background.
 */
@Composable
private fun SystemBars() {
    val palette = LocalPalette.current
    val view = LocalView.current

    LaunchedEffect(palette.isDark) {
        val window = (view.context as? ComponentActivity)?.window ?: return@LaunchedEffect
        window.setBackgroundDrawable(ColorDrawable(palette.background.toArgb()))
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !palette.isDark
            isAppearanceLightNavigationBars = !palette.isDark
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrayerApp(viewModel: PrayerViewModel) {
    var tab by remember { mutableStateOf(Tab.TODAY) }
    val home by viewModel.home.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val now = rememberCurrentInstant()
    val palette = LocalPalette.current

    Scaffold(
        containerColor = palette.background,
        bottomBar = {
            NavigationBar(
                containerColor = palette.surface,
                windowInsets = NavigationBarDefaults.windowInsets,
            ) {
                for (entry in Tab.entries) {
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = {},
                        label = { Text(stringResource(entry.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = palette.brass,
                            unselectedTextColor = palette.inkMuted,
                            indicatorColor = palette.surfaceRaised,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(palette.background)
                .padding(padding),
        ) {
            when (tab) {
                Tab.TODAY -> TodayScreen(state = home, now = now, onRefresh = viewModel::refresh)
                Tab.SETTINGS -> SettingsScreen(preferences = settings, viewModel = viewModel)
            }
        }
    }
}

/**
 * The current instant, ticking once a second.
 *
 * `produceState` ties the loop to composition, so it stops the moment the screen leaves the
 * foreground — a per-second timer that outlived the UI would be a battery leak in an app whose whole
 * argument is that it does not wake the device.
 */
@Composable
private fun rememberCurrentInstant(): Instant =
    produceState(initialValue = Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(TICK_MILLIS)
        }
    }.value

private const val TICK_MILLIS = 1_000L
