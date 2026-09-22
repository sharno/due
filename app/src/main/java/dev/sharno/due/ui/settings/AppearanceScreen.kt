package dev.sharno.due.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sharno.due.ThemeMode
import dev.sharno.due.ThemeSettings
import dev.sharno.due.ui.components.ColorPickerDialog
import dev.sharno.due.ui.components.ColorSwatchRow
import dev.sharno.due.ui.components.DetailTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceScreen(
    theme: ThemeSettings,
    onModeChange: (ThemeMode) -> Unit,
    onSeedChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(topBar = { DetailTopBar("Appearance", onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Theme", style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = theme.mode == mode,
                            onClick = { onModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        ) { Text(mode.label) }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Primary colour", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Every other colour in the app is generated from this one, light and dark.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ColorSwatchRow(selectedArgb = theme.seedArgb, onSelect = onSeedChange)
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Pick a custom colour") }
            }

            ThemePreview()
        }
    }

    if (showPicker) {
        ColorPickerDialog(
            initialArgb = theme.seedArgb,
            onDismiss = { showPicker = false },
            onConfirm = { argb ->
                showPicker = false
                onSeedChange(argb)
            },
        )
    }
}

/** Shows the generated scheme on the same widgets the task list uses. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemePreview() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Preview", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = true, onCheckedChange = null)
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text("Practise scales", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Due today at 19:00",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {}) { Text("Save") }
                    FilterChip(selected = true, onClick = {}, label = { Text("Guitar") })
                }
                Text(
                    "Overdue since yesterday",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
