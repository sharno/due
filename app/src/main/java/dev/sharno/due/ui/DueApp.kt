package dev.sharno.due.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sharno.due.EncryptedTodoBackup
import dev.sharno.due.TaskScheduler
import dev.sharno.due.TodoBackup
import dev.sharno.due.Skill
import dev.sharno.due.SkillStats
import dev.sharno.due.SkillStatsCalculator
import dev.sharno.due.Todo
import dev.sharno.due.TodoViewModel
import dev.sharno.due.ThemeSettings
import dev.sharno.due.ui.components.ConfirmDialog
import dev.sharno.due.ui.settings.AppearanceScreen
import dev.sharno.due.ui.settings.BackupPassphraseDialog
import dev.sharno.due.ui.settings.SettingsScreen
import dev.sharno.due.ui.rating.RatingSheet
import dev.sharno.due.ui.skills.SkillEditorDialog
import dev.sharno.due.ui.skills.SkillStatsScreen
import dev.sharno.due.ui.skills.SkillsScreen
import dev.sharno.due.ui.skills.StatsSort
import dev.sharno.due.ui.skills.suggestSkillColor
import dev.sharno.due.ui.tasks.TodoEditorDialog
import dev.sharno.due.ui.tasks.TasksScreen

@Composable
internal fun DueApp(viewModel: TodoViewModel = viewModel()) {
    val todos by viewModel.todos.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val automaticBackupError by viewModel.automaticBackupError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val uiPreferences by viewModel.uiPreferences.collectAsStateWithLifecycle()
    val skillGraph by viewModel.skillGraph.collectAsStateWithLifecycle()
    val pendingRating by viewModel.pendingRating.collectAsStateWithLifecycle()
    val skillStats = remember(skillGraph) { SkillStatsCalculator.summarise(skillGraph) }
    val skillsByTodoId = remember(skillGraph) { skillGraph.skillsByTodoId() }
    val activeSkills = remember(skillGraph) { skillGraph.activeSkills }
    val unratedCompletions = remember(skillGraph) { skillGraph.unratedCompletions() }
    var screen by rememberSaveable { mutableStateOf(DueScreen.TASKS) }
    var showNewTodo by remember { mutableStateOf(false) }
    var editingTodo by remember { mutableStateOf<Todo?>(null) }
    var pendingDelete by remember { mutableStateOf<Todo?>(null) }
    var editingSkill by remember { mutableStateOf<Skill?>(null) }
    var showNewSkill by remember { mutableStateOf(false) }
    var pendingSkillDelete by remember { mutableStateOf<SkillStats?>(null) }
    var statsSort by remember { mutableStateOf(StatsSort.AVERAGE) }
    var editingSkillIds by remember { mutableStateOf(emptySet<String>()) }
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

    BackHandler(enabled = screen != DueScreen.TASKS) {
        screen = if (screen == DueScreen.APPEARANCE) DueScreen.SETTINGS else DueScreen.TASKS
    }

    // Loading a task's current skills is a suspend call, so it happens when the editor opens.
    LaunchedEffect(editingTodo?.id) {
        editingSkillIds = editingTodo?.let { skillsByTodoId[it.id].orEmpty().mapTo(mutableSetOf(), Skill::id) }
            ?: emptySet()
    }

    when (screen) {
        DueScreen.TASKS -> TasksScreen(
            todos = todos,
            filter = uiPreferences.taskFilter,
            onFilterChange = viewModel::setTaskFilter,
            skillsByTodoId = skillsByTodoId,
            onCompleteChanged = viewModel::setCompleted,
            onRequestDelete = { todo -> pendingDelete = todo },
            onEdit = { todo -> editingTodo = todo },
            onAddTodo = { showNewTodo = true },
            onOpenSettings = { screen = DueScreen.SETTINGS },
            onOpenSkills = { screen = DueScreen.SKILLS },
            onOpenStats = { screen = DueScreen.SKILL_STATS },
            unratedSessions = unratedCompletions.size,
            onRateUnrated = {
                unratedCompletions.firstOrNull()?.let(viewModel::rateCompletion)
            },
        )

        DueScreen.SKILLS -> SkillsScreen(
            stats = skillStats,
            onBack = { screen = DueScreen.TASKS },
            onAdd = { showNewSkill = true },
            onEdit = { editingSkill = it },
            onArchive = { viewModel.archiveSkill(it.id) },
            onRestore = { viewModel.restoreSkill(it.id) },
            onRequestDelete = { pendingSkillDelete = it },
        )

        DueScreen.SKILL_STATS -> SkillStatsScreen(
            stats = skillStats,
            sort = statsSort,
            onSortChange = { statsSort = it },
            onBack = { screen = DueScreen.TASKS },
        )

        DueScreen.SETTINGS -> SettingsScreen(
            remindersEnabled = settings.remindersEnabled,
            automaticBackupConfigured = settings.automaticBackupConfigured,
            automaticBackupError = automaticBackupError,
            theme = theme ?: ThemeSettings(),
            onRemindersEnabledChanged = viewModel::setRemindersEnabled,
            onOpenAppearance = { screen = DueScreen.APPEARANCE },
            onExport = { exportLauncher.launch(TodoBackup.FILE_NAME) },
            onImport = { importLauncher.launch(arrayOf(TodoBackup.MIME_TYPE, "text/plain")) },
            onConfigureAutomaticBackup = {
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
                encryptedRestoreLauncher.launch(arrayOf(EncryptedTodoBackup.MIME_TYPE, "text/plain"))
            },
            onBack = { screen = DueScreen.TASKS },
        )

        DueScreen.APPEARANCE -> AppearanceScreen(
            theme = theme ?: ThemeSettings(),
            onModeChange = viewModel::setThemeMode,
            onSeedChange = viewModel::setThemeSeed,
            onBack = { screen = DueScreen.SETTINGS },
        )
    }

    if (showNewTodo) {
        TodoEditorDialog(
            initial = null,
            allSkills = activeSkills,
            initialSkillIds = emptySet(),
            onDismiss = { showNewTodo = false },
            onCreateSkill = { showNewSkill = true },
            onSave = { title, dueAtMillis, recurrence, skillIds ->
                viewModel.add(title, dueAtMillis, recurrence, skillIds)
                showNewTodo = false
            },
        )
    }

    editingTodo?.let { todo ->
        TodoEditorDialog(
            initial = todo,
            allSkills = activeSkills,
            initialSkillIds = editingSkillIds,
            onDismiss = { editingTodo = null },
            onCreateSkill = { showNewSkill = true },
            onSave = { title, dueAtMillis, recurrence, skillIds ->
                viewModel.update(todo, title, dueAtMillis, recurrence)
                viewModel.setSkillsForTask(todo.id, skillIds)
                editingTodo = null
            },
        )
    }

    pendingDelete?.let { todo ->
        ConfirmDialog(
            title = "Delete this task?",
            message = "\"${todo.title}\" will be removed. This cannot be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                pendingDelete = null
                viewModel.delete(todo)
            },
            onDismiss = { pendingDelete = null },
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

    if (showNewSkill) {
        SkillEditorDialog(
            initial = null,
            existingNameKeys = skillGraph.skills.mapTo(mutableSetOf(), Skill::nameKey),
            suggestedColorArgb = suggestSkillColor(skillGraph.skills),
            onDismiss = { showNewSkill = false },
            onSave = { name, colorArgb ->
                showNewSkill = false
                viewModel.addSkill(name, colorArgb) { result ->
                    showOperationResult(result, "Skill added")
                }
            },
        )
    }

    editingSkill?.let { skill ->
        SkillEditorDialog(
            initial = skill,
            existingNameKeys = skillGraph.skills.mapTo(mutableSetOf(), Skill::nameKey),
            suggestedColorArgb = skill.colorArgb,
            onDismiss = { editingSkill = null },
            onSave = { name, colorArgb ->
                editingSkill = null
                viewModel.updateSkill(skill.id, name, colorArgb) { result ->
                    showOperationResult(result, "Skill updated")
                }
            },
        )
    }

    pendingSkillDelete?.let { entry ->
        ConfirmDialog(
            title = "Delete \"${entry.skill.name}\" permanently?",
            message = buildString {
                append("This removes the skill and its ")
                append(entry.ratedSessions)
                append(" rating")
                if (entry.ratedSessions != 1) append("s")
                append(" from your stats for good.")
                if (entry.taskCount > 0) {
                    append(" It is also detached from ")
                    append(entry.taskCount)
                    append(" task")
                    if (entry.taskCount != 1) append("s")
                    append(".")
                }
                append(" Archiving keeps the history instead.")
            },
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                pendingSkillDelete = null
                viewModel.deleteSkillPermanently(entry.skill.id)
            },
            onDismiss = { pendingSkillDelete = null },
        )
    }

    pendingRating?.let { pending ->
        RatingSheet(
            pending = pending,
            onSkip = viewModel::skipRating,
            onSave = { ratings -> viewModel.submitRatings(pending.completionId, ratings) },
        )
    }
}
