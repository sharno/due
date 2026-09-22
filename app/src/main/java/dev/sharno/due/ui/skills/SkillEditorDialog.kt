package dev.sharno.due.ui.skills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sharno.due.Skill
import dev.sharno.due.ui.components.ColorPickerDialog
import dev.sharno.due.ui.components.ColorSwatchRow
import dev.sharno.due.ui.components.PRESET_SEEDS

@Composable
internal fun SkillEditorDialog(
    initial: Skill?,
    existingNameKeys: Set<String>,
    suggestedColorArgb: Int,
    onDismiss: () -> Unit,
    onSave: (name: String, colorArgb: Int) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var colorArgb by remember { mutableIntStateOf(initial?.colorArgb ?: suggestedColorArgb) }
    var showPicker by remember { mutableStateOf(false) }

    val trimmed = name.trim()
    val duplicate = trimmed.isNotBlank() &&
        Skill.normalizeName(trimmed) != initial?.nameKey &&
        Skill.normalizeName(trimmed) in existingNameKeys

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New skill" else "Edit skill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Skill name") },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = if (duplicate) {
                        { Text("You already have a skill with this name") }
                    } else {
                        null
                    },
                )
                Text("Colour", style = MaterialTheme.typography.labelLarge)
                ColorSwatchRow(selectedArgb = colorArgb, onSelect = { colorArgb = it })
                TextButton(onClick = { showPicker = true }) { Text("Custom colour") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = trimmed.isNotBlank() && !duplicate,
                onClick = { onSave(trimmed, colorArgb) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showPicker) {
        ColorPickerDialog(
            initialArgb = colorArgb,
            onDismiss = { showPicker = false },
            onConfirm = {
                showPicker = false
                colorArgb = it
            },
        )
    }
}

/** Picks the next preset colour that is not already taken, so new skills look distinct. */
internal fun suggestSkillColor(existing: List<Skill>): Int {
    val used = existing.mapTo(mutableSetOf(), Skill::colorArgb)
    return PRESET_SEEDS.firstOrNull { it !in used } ?: PRESET_SEEDS[existing.size % PRESET_SEEDS.size]
}
