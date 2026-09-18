package dev.sharno.due

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    val context = LocalContext.current
    var showNewTodo by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

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
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showNewTodo = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add todo")
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
                onSave = { title, dueAtMillis ->
                    viewModel.add(title, dueAtMillis)
                    showNewTodo = false
                },
            )
        }

        if (showSettings) {
            SettingsDialog(
                remindersEnabled = settings.remindersEnabled,
                onRemindersEnabledChanged = viewModel::setRemindersEnabled,
                onExport = {
                    showSettings = false
                    exportLauncher.launch(TodoBackup.FILE_NAME)
                },
                onImport = {
                    showSettings = false
                    importLauncher.launch(arrayOf(TodoBackup.MIME_TYPE, "text/plain"))
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
    onRemindersEnabledChanged: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
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
            }
            IconButton(onClick = { onDelete(todo) }) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete ${todo.title}")
            }
        }
    }
}

private fun todoStatus(todo: Todo, overdue: Boolean): String = when {
    todo.completed -> "Completed"
    overdue -> "Overdue · ${formatDueAt(todo.dueAtMillis)}"
    else -> "Due ${formatDueAt(todo.dueAtMillis)}"
}

@Composable
private fun NewTodoDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, dueAtMillis: Long) -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var dueAtMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showTitleError by remember { mutableStateOf(false) }

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
                    supportingText = if (showTitleError) {
                        { Text("A todo needs a title") }
                    } else {
                        null
                    },
                )
                Text("Due date and time", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val current = dueAtMillis.asLocalDateTime()
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    dueAtMillis = LocalDateTime.of(
                                        LocalDate.of(year, month + 1, day),
                                        dueAtMillis.asLocalDateTime().toLocalTime(),
                                    ).toMillis()
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
                Text(
                    "Both are required and already set to today and now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isBlank()) {
                        showTitleError = true
                    } else {
                        onSave(title, dueAtMillis)
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
