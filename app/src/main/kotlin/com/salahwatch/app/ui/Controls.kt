package com.salahwatch.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The interface vocabulary: one control per kind of decision, used consistently.
 *
 * The rule applied throughout — **radio buttons when every option fits on screen, a dropdown when
 * they do not**. Two schools are a radio pair; twenty-three calculation methods are a dropdown.
 * Rendering either as a row of chips, as this app did before, made a long list unusable and a short
 * one ambiguous about whether more than one could be picked.
 *
 * Every colour here comes from [LocalPalette], never from [PrayerColors] directly. The constants in
 * `PrayerColors` are the *dark* values; reading them here is what left the light theme rendering
 * dark cards on a pale background.
 */

/** A titled block. The label is structure, so it is set as structure: small, wide, quiet. */
@Composable
internal fun SettingsSection(
    title: String,
    description: String? = null,
    content: @Composable ColumnScopeAlias.() -> Unit,
) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = palette.inkFaint,
        )
        description?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surface)
                .padding(vertical = 4.dp),
            content = content,
        )
    }
}

internal typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope

/**
 * A single choice from a short list.
 *
 * The whole row is the target, not just the button — a 48dp row is far easier to hit than a 20dp
 * circle, and `Role.RadioButton` keeps that legible to accessibility services.
 */
@Composable
internal fun <T> RadioRow(
    option: T,
    selected: T,
    label: (T) -> String,
    supporting: ((T) -> String?)? = null,
    onSelect: (T) -> Unit,
) {
    val palette = LocalPalette.current
    val isSelected = option == selected
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton) { onSelect(option) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = palette.brass,
                unselectedColor = palette.inkFaint,
            ),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label(option),
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) palette.ink else palette.inkMuted,
            )
            supporting?.invoke(option)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = palette.inkFaint)
            }
        }
    }
}

/** A choice from a list too long to show at once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> Dropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val palette = LocalPalette.current
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                focusedTextColor = palette.ink,
                unfocusedTextColor = palette.ink,
                focusedBorderColor = palette.brass,
                unfocusedBorderColor = palette.line,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (option in options) {
                DropdownMenuItem(
                    text = {
                        Text(
                            optionLabel(option),
                            color = if (option == selected) palette.brass else palette.ink,
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * A whole-minute correction, with the value always visible.
 *
 * [onAdjust] reports a **delta**, not the new total. The difference matters: the value shown here
 * arrives through a write to disk and back, so a second tap that lands before the first has come
 * round reads the same stale number and recomputes the same total — which is why tapping `+` five
 * times used to move the value by one, and why stepping back down appeared to do nothing. A delta
 * is applied to whatever is stored at the moment it is applied, so every tap counts.
 */
@Composable
internal fun MinuteStepper(
    label: String,
    value: Int,
    allowNegative: Boolean,
    onAdjust: (delta: Int) -> Unit,
) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = palette.ink)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepperButton("−", enabled = allowNegative || value > 0) { onAdjust(-1) }
            Text(
                text = if (value > 0) "+$value" else "$value",
                style = TimeStyle,
                color = if (value == 0) palette.inkFaint else palette.brass,
                modifier = Modifier.width(44.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            StepperButton("+", enabled = true) { onAdjust(1) }
        }
    }
}

@Composable
private fun StepperButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Text(
        text = symbol,
        style = MaterialTheme.typography.titleMedium,
        color = if (enabled) palette.ink else palette.inkFaint,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surfaceRaised)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(top = 8.dp),
    )
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        color = LocalPalette.current.line,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}
