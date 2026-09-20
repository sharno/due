package dev.sharno.due

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DueApp() }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            TaskScheduler.synchronize(this@MainActivity)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueApp(viewModel: TodoViewModel = viewModel()) {
    val todos by viewModel.todos.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val automaticBackupError by viewModel.automaticBackupError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showNewTodo by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingAutomaticBackupUri by remember { mutableStateOf<Uri?>(null) }
    var pendingEncryptedRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingRestorePassphraseUri by remember { mutableStateOf<Uri?>(null) }

    fun showOperationResult(result: Result<Unit>, successMessage: String) {
        val message = result.fold(
            onSuccess = { successMessage },
            onFailure = { error -> error.message ?: "The operation failed" },
        )
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(TodoBackup.MIME_TYPE),
    ) { uri ->
        if (uri != null) {
            viewModel.export(uri) { result -> showOperationResult(result, "Todos exported") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingImportUri = uri }
    val automaticBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(EncryptedTodoBackup.MIME_TYPE),
    ) { uri ->
        if (uri != null) {
            val result = runCatching { persistAutomaticBackupPermission(context, uri) }
            if (result.isSuccess) {
                pendingAutomaticBackupUri = uri
            } else {
                showOperationResult(result, "Automatic backup destination selected")
            }
        }
    }
    val encryptedRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingEncryptedRestoreUri = uri }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            TaskScheduler.synchronize(context, todos, settings)
        }
    }

    LaunchedEffect(Unit) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Due") },
                    actions = {
                        if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            !canScheduleExactAlarms(context)
                        ) {
                            TextButton(onClick = { context.startActivity(exactAlarmsSettingsIntent()) }) {
                                Text("Enable exact reminders")
                            }
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_settings),
                                contentDescription = "Settings",
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showNewTodo = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = "Add todo",
                    )
                }
            },
        ) { padding ->
            if (todos.isEmpty()) {
                EmptyTodos(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            } else {
                TodoList(
                    todos = todos,
                    onCompleteChanged = viewModel::setCompleted,
                    onDelete = viewModel::delete,
                    modifier = Modifier.padding(padding),
                )
            }
        }

        if (showNewTodo) {
            NewTodoDialog(
                onDismiss = { showNewTodo = false },
                onSave = { title, dueAtMillis, recurrence ->
                    viewModel.add(title, dueAtMillis, recurrence)
                    showNewTodo = false
                },
            )
        }

        if (showSettings) {
            SettingsDialog(
                remindersEnabled = settings.remindersEnabled,
                automaticBackupConfigured = settings.automaticBackupConfigured,
                automaticBackupError = automaticBackupError,
                onRemindersEnabledChanged = viewModel::setRemindersEnabled,
                onExport = {
                    showSettings = false
                    exportLauncher.launch(TodoBackup.FILE_NAME)
                },
                onImport = {
                    showSettings = false
                    importLauncher.launch(arrayOf(TodoBackup.MIME_TYPE, "text/plain"))
                },
                onConfigureAutomaticBackup = {
                    showSettings = false
                    automaticBackupLauncher.launch(EncryptedTodoBackup.FILE_NAME)
                },
                onBackupNow = {
                    viewModel.backupNow { result -> showOperationResult(result, "Encrypted backup updated") }
                },
                onDisableAutomaticBackup = {
                    viewModel.disableAutomaticBackup { result ->
                        showOperationResult(result, "Automatic backup disabled")
                    }
                },
                onRestoreEncrypted = {
                    showSettings = false
                    encryptedRestoreLauncher.launch(arrayOf(EncryptedTodoBackup.MIME_TYPE, "text/plain"))
                },
                onDismiss = { showSettings = false },
            )
        }

        pendingImportUri?.let { uri ->
            AlertDialog(
                onDismissRequest = { pendingImportUri = null },
                title = { Text("Import todos?") },
                text = { Text("This replaces all current todos with the selected backup.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingImportUri = null
                            viewModel.import(uri) { result -> showOperationResult(result, "Todos imported") }
                        },
                    ) { Text("Import") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingImportUri = null }) { Text("Cancel") }
                },
            )
        }

        pendingAutomaticBackupUri?.let { uri ->
            BackupPassphraseDialog(
                title = "Protect automatic backup",
                description = "Choose a passphrase for the encrypted file. Due protects it with this device's Android Keystore; keep the passphrase for restoring on another device.",
                confirmPassphrase = true,
                confirmLabel = "Enable backup",
                onDismiss = { pendingAutomaticBackupUri = null },
                onConfirm = { passphrase ->
                    pendingAutomaticBackupUri = null
                    viewModel.configureAutomaticBackup(uri, passphrase) { result ->
                        showOperationResult(result, "Encrypted automatic backup enabled")
                    }
                },
            )
        }

        pendingEncryptedRestoreUri?.let { uri ->
            AlertDialog(
                onDismissRequest = { pendingEncryptedRestoreUri = null },
                title = { Text("Restore encrypted backup?") },
                text = { Text("This replaces all current todos after the backup is decrypted.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingEncryptedRestoreUri = null
                            pendingRestorePassphraseUri = uri
                        },
                    ) { Text("Continue") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingEncryptedRestoreUri = null }) { Text("Cancel") }
                },
            )
        }

        pendingRestorePassphraseUri?.let { uri ->
            BackupPassphraseDialog(
                title = "Decrypt backup",
                description = "Enter the passphrase used when this encrypted backup was created.",
                confirmPassphrase = false,
                confirmLabel = "Restore",
                onDismiss = { pendingRestorePassphraseUri = null },
                onConfirm = { passphrase ->
                    pendingRestorePassphraseUri = null
                    viewModel.restoreEncrypted(uri, passphrase) { result ->
                        showOperationResult(result, "Encrypted todos restored")
                    }
                },
            )
        }
    }
}

@Composable
private fun EmptyTodos(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Nothing due", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Add a task. Its date and time start as today and now.")
        }
    }
}

@Composable
private fun SettingsDialog(
    remindersEnabled: Boolean,
    automaticBackupConfigured: Boolean,
    automaticBackupError: String?,
    onRemindersEnabledChanged: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onConfigureAutomaticBackup: () -> Unit,
    onBackupNow: () -> Unit,
    onDisableAutomaticBackup: () -> Unit,
    onRestoreEncrypted: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Overdue reminders", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Keep an ongoing notification until tasks are completed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = remindersEnabled,
                        onCheckedChange = onRemindersEnabledChanged,
                    )
                }
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
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun BackupPassphraseDialog(
    title: String,
    description: String,
    confirmPassphrase: Boolean,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var repeatedPassphrase by remember { mutableStateOf("") }
    val mismatch = confirmPassphrase && repeatedPassphrase.isNotEmpty() && passphrase != repeatedPassphrase

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(description, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                if (confirmPassphrase) {
                    OutlinedTextField(
                        value = repeatedPassphrase,
                        onValueChange = { repeatedPassphrase = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Repeat passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = mismatch,
                        supportingText = if (mismatch) {
                            { Text("Passphrases do not match") }
                        } else {
                            null
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = passphrase.isNotEmpty() && (!confirmPassphrase || passphrase == repeatedPassphrase),
                onClick = { onConfirm(passphrase) },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TodoList(
    todos: List<Todo>,
    onCompleteChanged: (Todo, Boolean) -> Unit,
    onDelete: (Todo) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(todos, key = Todo::id) { todo ->
            TodoRow(todo, onCompleteChanged, onDelete)
        }
    }
}

@Composable
private fun TodoRow(
    todo: Todo,
    onCompleteChanged: (Todo, Boolean) -> Unit,
    onDelete: (Todo) -> Unit,
) {
    val overdue = !todo.completed && todo.dueAtMillis <= System.currentTimeMillis()
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = todo.completed,
                onCheckedChange = { checked -> onCompleteChanged(todo, checked) },
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (todo.completed) TextDecoration.LineThrough else TextDecoration.None,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = todoStatus(todo, overdue),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (overdue) FontWeight.SemiBold else FontWeight.Normal,
                )
                todo.recurrence?.let { recurrence ->
                    Text(
                        text = recurrenceSummary(recurrence),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { onDelete(todo) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = "Delete ${todo.title}",
                )
            }
        }
    }
}

private fun todoStatus(todo: Todo, overdue: Boolean): String = when {
    todo.completed -> "Completed"
    overdue -> "Overdue · ${formatDueAt(todo.dueAtMillis)}"
    else -> "Due ${formatDueAt(todo.dueAtMillis)}"
}

private fun recurrenceSummary(rule: RecurrenceRule): String = when (rule.frequency) {
    RecurrenceFrequency.DAILY -> "Repeats every ${rule.interval} day${if (rule.interval == 1) "" else "s"}"
    RecurrenceFrequency.WEEKDAYS -> "Repeats every weekday"
    RecurrenceFrequency.WEEKLY -> "Repeats every ${rule.interval} week${if (rule.interval == 1) "" else "s"} on " +
        rule.daysOfWeek.sortedBy(DayOfWeek::getValue).joinToString(", ") { it.shortLabel() }
    RecurrenceFrequency.MONTHLY -> "Repeats every ${rule.interval} month${if (rule.interval == 1) "" else "s"} on day ${rule.dayOfMonth}"
    RecurrenceFrequency.YEARLY -> "Repeats every ${rule.interval} year${if (rule.interval == 1) "" else "s"}"
}

private fun DayOfWeek.shortLabel(): String = getDisplayName(TextStyle.SHORT, Locale.getDefault())

private enum class RepeatPattern(val label: String) {
    NONE("Does not repeat"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Annually"),
    WEEKDAYS("Every weekday"),
    CUSTOM("Custom"),
}

private enum class RepeatUnit(val label: String) {
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year"),
}

private enum class RepeatEndMode(val label: String) {
    NEVER("Never"),
    ON_DATE("On date"),
    AFTER_OCCURRENCES("After occurrences"),
}

@Composable
private fun NewTodoDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, dueAtMillis: Long, recurrence: RecurrenceRule?) -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var dueAtMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showTitleError by remember { mutableStateOf(false) }
    var recurrenceError by remember { mutableStateOf<String?>(null) }
    var repeatPattern by remember { mutableStateOf(RepeatPattern.NONE) }
    var customUnit by remember { mutableStateOf(RepeatUnit.WEEK) }
    var customInterval by remember { mutableStateOf("1") }
    var selectedWeekdays by remember {
        mutableStateOf(setOf(dueAtMillis.asLocalDateTime().dayOfWeek))
    }
    var customMonthDay by remember {
        mutableStateOf(dueAtMillis.asLocalDateTime().dayOfMonth.toString())
    }
    var repeatEndMode by remember { mutableStateOf(RepeatEndMode.NEVER) }
    var repeatEndDateMillis by remember { mutableLongStateOf(dueAtMillis) }
    var repeatOccurrences by remember { mutableStateOf("10") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New todo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        showTitleError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("What needs doing?") },
                    singleLine = true,
                    isError = showTitleError,
                )
                Text("Due date and time", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val current = dueAtMillis.asLocalDateTime()
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    val previousDueAtMillis = dueAtMillis
                                    val previousDate = previousDueAtMillis.asLocalDateTime().toLocalDate()
                                    val nextDate = LocalDate.of(year, month + 1, day)
                                    dueAtMillis = LocalDateTime.of(
                                        nextDate,
                                        dueAtMillis.asLocalDateTime().toLocalTime(),
                                    ).toMillis()
                                    if (selectedWeekdays == setOf(previousDate.dayOfWeek)) {
                                        selectedWeekdays = setOf(nextDate.dayOfWeek)
                                    }
                                    if (customMonthDay == previousDate.dayOfMonth.toString()) {
                                        customMonthDay = nextDate.dayOfMonth.toString()
                                    }
                                    if (repeatEndDateMillis == previousDueAtMillis) {
                                        repeatEndDateMillis = dueAtMillis
                                    }
                                },
                                current.year,
                                current.monthValue - 1,
                                current.dayOfMonth,
                            ).show()
                        },
                    ) { Text(formatDate(dueAtMillis)) }
                    Button(
                        onClick = {
                            val current = dueAtMillis.asLocalDateTime()
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    dueAtMillis = LocalDateTime.of(
                                        dueAtMillis.asLocalDateTime().toLocalDate(),
                                        LocalTime.of(hour, minute),
                                    ).toMillis()
                                },
                                current.hour,
                                current.minute,
                                true,
                            ).show()
                        },
                    ) { Text(formatTime(dueAtMillis)) }
                }
                Text("Repeat", style = MaterialTheme.typography.labelLarge)
                RepeatPatternPicker(
                    selected = repeatPattern,
                    onSelected = {
                        repeatPattern = it
                        recurrenceError = null
                    },
                )
                if (repeatPattern != RepeatPattern.NONE) {
                    when (repeatPattern) {
                        RepeatPattern.WEEKLY -> {
                            Text("Repeat on", style = MaterialTheme.typography.labelLarge)
                            WeekdayChips(
                                selected = selectedWeekdays,
                                onSelected = { selectedWeekdays = it },
                            )
                        }

                        RepeatPattern.MONTHLY -> {
                            Text("On day ${dueAtMillis.asLocalDateTime().dayOfMonth}")
                        }

                        RepeatPattern.YEARLY -> {
                            Text("On ${formatDate(dueAtMillis)}")
                        }

                        RepeatPattern.CUSTOM -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("Every")
                                OutlinedTextField(
                                    value = customInterval,
                                    onValueChange = {
                                        if (it.all(Char::isDigit)) customInterval = it
                                    },
                                    modifier = Modifier.width(84.dp),
                                    singleLine = true,
                                )
                                RepeatUnitPicker(
                                    selected = customUnit,
                                    onSelected = { customUnit = it },
                                )
                            }
                            if (customUnit == RepeatUnit.WEEK) {
                                Text("Repeat on", style = MaterialTheme.typography.labelLarge)
                                WeekdayChips(
                                    selected = selectedWeekdays,
                                    onSelected = { selectedWeekdays = it },
                                )
                            }
                            if (customUnit == RepeatUnit.MONTH) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text("On day")
                                    OutlinedTextField(
                                        value = customMonthDay,
                                        onValueChange = {
                                            if (it.all(Char::isDigit)) customMonthDay = it
                                        },
                                        modifier = Modifier.width(84.dp),
                                        singleLine = true,
                                    )
                                }
                            }
                        }

                        RepeatPattern.DAILY,
                        RepeatPattern.WEEKDAYS,
                        RepeatPattern.NONE,
                        -> Unit
                    }

                    Text("Ends", style = MaterialTheme.typography.labelLarge)
                    RepeatEndPicker(
                        selected = repeatEndMode,
                        onSelected = { repeatEndMode = it },
                    )
                    when (repeatEndMode) {
                        RepeatEndMode.NEVER -> Unit
                        RepeatEndMode.ON_DATE -> {
                            Button(
                                onClick = {
                                    val current = repeatEndDateMillis.asLocalDateTime()
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, day ->
                                            repeatEndDateMillis = LocalDate.of(year, month + 1, day)
                                                .atStartOfDay(ZoneId.systemDefault())
                                                .toInstant()
                                                .toEpochMilli()
                                        },
                                        current.year,
                                        current.monthValue - 1,
                                        current.dayOfMonth,
                                    ).apply { datePicker.minDate = dueAtMillis }.show()
                                },
                            ) { Text(formatDate(repeatEndDateMillis)) }
                        }

                        RepeatEndMode.AFTER_OCCURRENCES -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = repeatOccurrences,
                                    onValueChange = {
                                        if (it.all(Char::isDigit)) repeatOccurrences = it
                                    },
                                    modifier = Modifier.width(100.dp),
                                    singleLine = true,
                                )
                                Text("occurrences")
                            }
                        }
                    }
                }
                recurrenceError?.let { error ->
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isBlank()) {
                        showTitleError = true
                    } else {
                        runCatching {
                            recurrenceFromSelection(
                                pattern = repeatPattern,
                                dueAtMillis = dueAtMillis,
                                selectedWeekdays = selectedWeekdays,
                                customUnit = customUnit,
                                customInterval = customInterval,
                                customMonthDay = customMonthDay,
                                endMode = repeatEndMode,
                                endDateMillis = repeatEndDateMillis,
                                occurrences = repeatOccurrences,
                            )
                        }.fold(
                            onSuccess = { recurrence -> onSave(title, dueAtMillis, recurrence) },
                            onFailure = { error -> recurrenceError = error.message ?: "Invalid repeat settings" },
                        )
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RepeatPatternPicker(
    selected: RepeatPattern,
    onSelected: (RepeatPattern) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RepeatPattern.values().forEach { pattern ->
                DropdownMenuItem(
                    text = { Text(pattern.label) },
                    onClick = {
                        expanded = false
                        onSelected(pattern)
                    },
                )
            }
        }
    }
}

@Composable
private fun RepeatUnitPicker(
    selected: RepeatUnit,
    onSelected: (RepeatUnit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { expanded = true }) { Text(selected.label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RepeatUnit.values().forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit.label) },
                    onClick = {
                        expanded = false
                        onSelected(unit)
                    },
                )
            }
        }
    }
}

@Composable
private fun RepeatEndPicker(
    selected: RepeatEndMode,
    onSelected: (RepeatEndMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { expanded = true }) { Text(selected.label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RepeatEndMode.values().forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        expanded = false
                        onSelected(mode)
                    },
                )
            }
        }
    }
}

@Composable
private fun WeekdayChips(
    selected: Set<DayOfWeek>,
    onSelected: (Set<DayOfWeek>) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DayOfWeek.values().forEach { day ->
            FilterChip(
                selected = day in selected,
                onClick = {
                    onSelected(
                        if (day in selected) {
                            if (selected.size == 1) selected else selected - day
                        } else {
                            selected + day
                        },
                    )
                },
                label = { Text(day.shortLabel()) },
            )
        }
    }
}

private fun recurrenceFromSelection(
    pattern: RepeatPattern,
    dueAtMillis: Long,
    selectedWeekdays: Set<DayOfWeek>,
    customUnit: RepeatUnit,
    customInterval: String,
    customMonthDay: String,
    endMode: RepeatEndMode,
    endDateMillis: Long,
    occurrences: String,
): RecurrenceRule? {
    if (pattern == RepeatPattern.NONE) return null

    val dueDate = dueAtMillis.asLocalDateTime().toLocalDate()
    val end = when (endMode) {
        RepeatEndMode.NEVER -> RecurrenceEnd.Never
        RepeatEndMode.ON_DATE -> RecurrenceEnd.On(
            endDateMillis.asLocalDateTime().toLocalDate().also {
                require(!it.isBefore(dueDate)) { "The end date must be on or after the due date" }
            },
        )

        RepeatEndMode.AFTER_OCCURRENCES -> RecurrenceEnd.After(
            occurrences.toIntOrNull()?.also {
                require(it > 0) { "Occurrences must be greater than zero" }
            } ?: error("Enter a valid number of occurrences"),
        )
    }

    return when (pattern) {
        RepeatPattern.DAILY -> RecurrenceRule(RecurrenceFrequency.DAILY, end = end)
        RepeatPattern.WEEKLY -> RecurrenceRule(
            frequency = RecurrenceFrequency.WEEKLY,
            daysOfWeek = selectedWeekdays,
            end = end,
        )

        RepeatPattern.MONTHLY -> RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            dayOfMonth = dueDate.dayOfMonth,
            end = end,
        )

        RepeatPattern.YEARLY -> RecurrenceRule(
            frequency = RecurrenceFrequency.YEARLY,
            dayOfMonth = dueDate.dayOfMonth,
            monthOfYear = dueDate.monthValue,
            end = end,
        )

        RepeatPattern.WEEKDAYS -> RecurrenceRule(RecurrenceFrequency.WEEKDAYS, end = end)
        RepeatPattern.CUSTOM -> {
            val interval = customInterval.toIntOrNull()?.also {
                require(it > 0) { "The repeat interval must be greater than zero" }
            } ?: error("Enter a valid repeat interval")
            when (customUnit) {
                RepeatUnit.DAY -> RecurrenceRule(RecurrenceFrequency.DAILY, interval, end = end)
                RepeatUnit.WEEK -> RecurrenceRule(
                    frequency = RecurrenceFrequency.WEEKLY,
                    interval = interval,
                    daysOfWeek = selectedWeekdays,
                    end = end,
                )

                RepeatUnit.MONTH -> RecurrenceRule(
                    frequency = RecurrenceFrequency.MONTHLY,
                    interval = interval,
                    dayOfMonth = customMonthDay.toIntOrNull()?.also {
                        require(it in 1..31) { "The monthly day must be from 1 to 31" }
                    } ?: error("Enter a valid monthly day"),
                    end = end,
                )

                RepeatUnit.YEAR -> RecurrenceRule(
                    frequency = RecurrenceFrequency.YEARLY,
                    interval = interval,
                    dayOfMonth = dueDate.dayOfMonth,
                    monthOfYear = dueDate.monthValue,
                    end = end,
                )
            }
        }

        RepeatPattern.NONE -> null
    }
}

private val dueDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
private val dueTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dueDateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d · HH:mm")

private fun Long.asLocalDateTime(): LocalDateTime = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .toLocalDateTime()

private fun LocalDateTime.toMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun formatDate(millis: Long): String = millis.asLocalDateTime().toLocalDate().format(dueDateFormatter)

private fun formatTime(millis: Long): String = millis.asLocalDateTime().toLocalTime().format(dueTimeFormatter)

private fun formatDueAt(millis: Long): String = millis.asLocalDateTime().format(dueDateTimeFormatter)

@RequiresApi(Build.VERSION_CODES.S)
private fun exactAlarmsSettingsIntent(): Intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)

private fun canScheduleExactAlarms(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

private fun persistAutomaticBackupPermission(context: Context, uri: Uri) {
    val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    try {
        context.contentResolver.takePersistableUriPermission(uri, readWrite)
    } catch (first: SecurityException) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (second: SecurityException) {
            throw IllegalStateException(
                "The selected storage provider does not support persistent automatic backups",
                second,
            )
        }
    }
}
