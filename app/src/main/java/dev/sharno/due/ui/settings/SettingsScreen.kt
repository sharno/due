package dev.sharno.due.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.sharno.due.R
import dev.sharno.due.ThemeSettings
import dev.sharno.due.ui.components.DetailTopBar
import dev.sharno.due.ui.components.SettingsRow
import dev.sharno.due.ui.components.SwatchDot

/**
 * Settings as a full screen rather than the AlertDialog it used to be: appearance, reminders and
 * six backup actions do not fit in a scrolling dialog on a phone in landscape.
 */
@Composable
internal fun SettingsScreen(
    remindersEnabled: Boolean,
    automaticBackupConfigured: Boolean,
    automaticBackupError: String?,
    theme: ThemeSettings,
    onRemindersEnabledChanged: (Boolean) -> Unit,
    onOpenAppearance: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onConfigureAutomaticBackup: () -> Unit,
    onBackupNow: () -> Unit,
    onDisableAutomaticBackup: () -> Unit,
    onRestoreEncrypted: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { DetailTopBar("Settings", onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsRow(
                title = "Appearance",
                description = "${theme.mode.label} theme",
                leadingIcon = painterResource(R.drawable.ic_palette),
                onClick = onOpenAppearance,
                trailing = { SwatchDot(color = Color(theme.seedArgb), selected = false) },
            )

            HorizontalDivider()

            SettingsRow(
                title = "Overdue reminders",
                description = "Keep an ongoing notification until tasks are completed.",
                trailing = {
                    Switch(checked = remindersEnabled, onCheckedChange = onRemindersEnabledChanged)
                },
            )

            HorizontalDivider()

            Text(
                "Back up your todos as a portable JSON file before changing devices or installing a different build channel.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Text("Export todos")
            }
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text("Import todos")
            }

            HorizontalDivider()

            Text(
                "Choose a Google Drive file (or another document provider) for an encrypted snapshot. Due does not use a Google account or sync service directly.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                if (automaticBackupConfigured) {
                    "Automatic encrypted backup is enabled. It is rewritten after each todo change."
                } else {
                    "Automatic encrypted backup is not configured."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            automaticBackupError?.let { error ->
                Text(
                    "Last automatic backup failed: $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(onClick = onConfigureAutomaticBackup, modifier = Modifier.fillMaxWidth()) {
                Text(if (automaticBackupConfigured) "Change backup file" else "Set up automatic backup")
            }
            if (automaticBackupConfigured) {
                Button(onClick = onBackupNow, modifier = Modifier.fillMaxWidth()) {
                    Text("Back up now")
                }
                TextButton(onClick = onDisableAutomaticBackup) {
                    Text("Disable automatic backup")
                }
            }
            Button(onClick = onRestoreEncrypted, modifier = Modifier.fillMaxWidth()) {
                Text("Restore encrypted backup")
            }
        }
    }
}
