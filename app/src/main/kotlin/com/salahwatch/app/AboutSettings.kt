package com.salahwatch.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salahwatch.app.ui.LocalPalette
import com.salahwatch.app.ui.SettingsSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The third-party notices.
 *
 * These are not a courtesy. adhan2 is MIT, whose terms require its copyright and permission notice
 * to accompany every copy of the software; the AndroidX, Kotlin and OkHttp stack is Apache 2.0,
 * whose section 4(a) requires that recipients be given the licence itself. Shipping the text inside
 * the APK is what satisfies both — a link would not, and a file sitting only in the repository
 * would not either.
 */
@Composable
internal fun AboutSection() {
    val palette = LocalPalette.current
    var showing by remember { mutableStateOf(false) }

    SettingsSection(
        title = stringResource(R.string.settings_about),
        description = stringResource(R.string.settings_about_desc),
    ) {
        Text(
            text = stringResource(R.string.action_view_licences),
            style = MaterialTheme.typography.bodyLarge,
            color = palette.brass,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { showing = true }
                .padding(horizontal = 14.dp, vertical = 14.dp),
        )
    }

    if (showing) {
        NoticesDialog(onDismiss = { showing = false })
    }
}

/**
 * The notices, read from the raw resource only once the reader asks for them.
 *
 * Fifteen kilobytes off the main thread, and not touched at all unless the dialog is opened — a
 * settings screen should not pay for a legal text nobody is looking at.
 */
@Composable
private fun NoticesDialog(onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    val resources = LocalResources.current

    val notices by produceState(initialValue = "", resources) {
        value = withContext(Dispatchers.IO) {
            resources.openRawResource(R.raw.third_party_notices)
                .bufferedReader()
                .use { it.readText() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Text(
                text = stringResource(R.string.settings_licences),
                style = MaterialTheme.typography.titleMedium,
                color = palette.ink,
            )
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = NOTICES_MAX_HEIGHT.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Monospace, and left-to-right regardless of the app's language: these are legal
                // texts in English, laid out in fixed-width columns that reflow into nonsense in a
                // proportional face or a mirrored layout.
                Text(
                    text = notices,
                    fontFamily = FontFamily.Monospace,
                    fontSize = NOTICES_TEXT_SP.sp,
                    color = palette.inkMuted,
                )
                Spacer(Modifier.height(8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close), color = palette.brass)
            }
        },
    )
}

private const val NOTICES_MAX_HEIGHT = 420
private const val NOTICES_TEXT_SP = 10
