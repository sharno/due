package dev.sharno.due

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** A completion that is waiting for the user to rate its skills. */
data class PendingRating(
    val completionId: String,
    val taskTitle: String,
    val skills: List<Skill>,
    val initialRatings: Map<String, Int>,
)

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TodoRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val skillRepository = SkillRepository(application)
    private val _todos = MutableStateFlow<List<Todo>>(emptyList())
    val todos = _todos.asStateFlow()
    private val _settings = MutableStateFlow(DueSettings())
    val settings = _settings.asStateFlow()
    private val _automaticBackupError = MutableStateFlow<String?>(null)
    val automaticBackupError = _automaticBackupError.asStateFlow()
    private val automaticBackupMutex = Mutex()

    /**
     * Appearance preferences. `null` means "not read from disk yet" so the first frame can be held
     * back rather than painted in the wrong colours and then corrected.
     *
     * Kept out of the [settings] flow on purpose: that one re-syncs alarms and rewrites the
     * encrypted backup on every emission.
     */
    private val _skillGraph = MutableStateFlow(SkillGraph())
    val skillGraph = _skillGraph.asStateFlow()
    private val _pendingRating = MutableStateFlow<PendingRating?>(null)
    val pendingRating = _pendingRating.asStateFlow()

    val theme: StateFlow<ThemeSettings?> = settingsRepository.themeSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiPreferences: StateFlow<UiPreferences> = settingsRepository.uiPreferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiPreferences())

    init {
        // Alarms depend on todos and settings only. Skills must never reach this collector, or
        // every drag of a rating slider would cancel and reschedule every alarm in the app.
        viewModelScope.launch {
            repository.observe()
                .combine(settingsRepository.settings, ::TodoState)
                .collect { state ->
                    _todos.value = state.todos
                    _settings.value = state.settings
                    TaskScheduler.synchronize(application, state.todos, state.settings)
                }
        }

        viewModelScope.launch {
            skillRepository.observeGraph().collect { graph -> _skillGraph.value = graph }
        }

        // The backup watches everything, but debounced: writing it runs PBKDF2 over 120k iterations
        // and pushes a file through a document provider, which is not something to do per keystroke.
        viewModelScope.launch {
            merge(
                repository.observe().map { },
                skillRepository.observeGraph().map { },
                settingsRepository.settings.map { },
            )
                .debounce(BACKUP_DEBOUNCE_MILLIS)
                .collectLatest {
                    if (settingsRepository.settings.first().automaticBackupConfigured) {
                        // Read in one transaction rather than stitching together three independently
                        // timed flow emissions, which could capture a link whose task has not been
                        // emitted yet and silently lose it on the next restore.
                        writeAutomaticBackup(repository.snapshot())
                    } else {
                        _automaticBackupError.value = null
                    }
                }
        }
    }

    fun add(
        title: String,
        dueAtMillis: Long,
        recurrence: RecurrenceRule?,
        skillIds: Set<String> = emptySet(),
    ) {
        require(title.isNotBlank()) { "A todo needs a title" }
        viewModelScope.launch {
            val todo = Todo.create(title, dueAtMillis, recurrence)
            repository.add(todo)
            if (skillIds.isNotEmpty()) {
                skillRepository.setSkillsForTask(todo.id, skillIds)
            }
        }
    }

    fun update(todo: Todo, title: String, dueAtMillis: Long, recurrence: RecurrenceRule?) {
        require(title.isNotBlank()) { "A todo needs a title" }
        viewModelScope.launch {
            repository.update(
                todo.copy(
                    title = title.trim(),
                    dueAtMillis = dueAtMillis,
                    recurrence = recurrence,
                    // A rule that no longer repeats cannot carry completed occurrences.
                    occurrencesCompleted = if (recurrence == null) 0 else todo.occurrencesCompleted,
                ),
            )
        }
    }

    fun setCompleted(todo: Todo, completed: Boolean) {
        viewModelScope.launch {
            // The task is committed before any sheet appears, so dismissing, backgrounding or
            // killing the app still leaves it complete.
            val completion = repository.setCompleted(todo.id, completed, CompletionSource.APP)
            if (completion != null) {
                _pendingRating.value = pendingRatingFor(completion)
            }
        }
    }

    /** Opens the rating sheet for a session that was completed from the notification. */
    fun rateCompletion(completion: TaskCompletion) {
        viewModelScope.launch {
            _pendingRating.value = pendingRatingFor(completion)
                ?: run {
                    skillRepository.dismissRatingPrompt(completion.id)
                    null
                }
        }
    }

    fun submitRatings(completionId: String, ratings: Map<String, Int>) {
        viewModelScope.launch {
            skillRepository.rate(completionId, ratings)
            skillRepository.dismissRatingPrompt(completionId)
            _pendingRating.value = null
        }
    }

    /** Skipping writes nothing; the session was already stamped as prompted when it was created. */
    fun skipRating() {
        val pending = _pendingRating.value ?: return
        _pendingRating.value = null
        viewModelScope.launch { skillRepository.dismissRatingPrompt(pending.completionId) }
    }

    fun dismissUnratedCompletion(completionId: String) {
        viewModelScope.launch { skillRepository.dismissRatingPrompt(completionId) }
    }

    private suspend fun pendingRatingFor(completion: TaskCompletion): PendingRating? {
        val graph = _skillGraph.value
        val skillIds = skillRepository.skillIdsForTask(completion.todoId)
        // A snapshot: the row may already have left the list, and a recurring task has by now
        // mutated to its next due date.
        val skills = graph.skills.filter { it.id in skillIds && !it.archived }
        if (skills.isEmpty()) return null

        val averages = SkillStatsCalculator.summarise(graph).associate { it.skill.id to it.average }
        return PendingRating(
            completionId = completion.id,
            taskTitle = completion.taskTitle,
            skills = skills,
            initialRatings = skills.associate { skill ->
                skill.id to (averages[skill.id]?.roundToInt() ?: DEFAULT_RATING)
            },
        )
    }

    fun addSkill(name: String, colorArgb: Int, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            onResult(runCatching { skillRepository.getOrCreate(name, colorArgb) }.map { })
        }
    }

    fun updateSkill(skillId: String, name: String, colorArgb: Int, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            onResult(runCatching { skillRepository.updateDetails(skillId, name, colorArgb) })
        }
    }

    fun archiveSkill(skillId: String) {
        viewModelScope.launch { skillRepository.archive(skillId) }
    }

    fun restoreSkill(skillId: String) {
        viewModelScope.launch { skillRepository.restore(skillId) }
    }

    fun deleteSkillPermanently(skillId: String) {
        viewModelScope.launch { skillRepository.deletePermanently(skillId) }
    }

    fun setSkillsForTask(todoId: String, skillIds: Set<String>) {
        viewModelScope.launch { skillRepository.setSkillsForTask(todoId, skillIds) }
    }

    fun delete(todo: Todo) {
        viewModelScope.launch {
            repository.delete(todo.id)
        }
    }

    fun setTaskFilter(filter: TaskFilter) {
        viewModelScope.launch {
            settingsRepository.setTaskFilter(filter)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setThemeSeed(argb: Int) {
        viewModelScope.launch {
            settingsRepository.setThemeSeed(argb)
        }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRemindersEnabled(enabled)
        }
    }

    fun configureAutomaticBackup(uri: Uri, passphrase: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    settingsRepository.configureAutomaticBackup(uri, passphrase)
                }
                val snapshot = withContext(Dispatchers.IO) { repository.snapshot() }
                writeAutomaticBackup(snapshot).getOrThrow()
            }
            if (result.isFailure) {
                _automaticBackupError.value = result.exceptionOrNull()?.message
            }
            onResult(result)
        }
    }

    fun backupNow(onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { repository.snapshot() }
            val result = writeAutomaticBackup(snapshot)
            onResult(result)
        }
    }

    fun disableAutomaticBackup(onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                settingsRepository.clearAutomaticBackup()
                _automaticBackupError.value = null
            }
            onResult(result)
        }
    }

    fun export(uri: Uri, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val contents = TodoBackup.encode(repository.snapshot())
                withContext(Dispatchers.IO) {
                    val output = getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?: error("Unable to open the selected file")
                    output.bufferedWriter().use { writer -> writer.write(contents) }
                }
            }
            onResult(result)
        }
    }

    fun import(uri: Uri, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val snapshot = withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: error("Unable to open the selected file")
                    input.bufferedReader().use { reader -> TodoBackup.decodeSnapshot(reader.readText()) }
                }
                repository.replaceAll(snapshot)
            }
            onResult(result)
        }
    }

    fun restoreEncrypted(uri: Uri, passphrase: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val snapshot = withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: error("Unable to open the selected file")
                    input.bufferedReader().use { reader ->
                        EncryptedTodoBackup.decode(reader.readText(), passphrase)
                    }
                }
                repository.replaceAll(snapshot)
            }
            onResult(result)
        }
    }

    private suspend fun writeAutomaticBackup(snapshot: DueSnapshot): Result<Unit> {
        val result = runCatching {
            automaticBackupMutex.withLock {
                val configuration = withContext(Dispatchers.IO) {
                    settingsRepository.automaticBackupConfiguration()
                } ?: error("Automatic backup is not configured")
                val contents = withContext(Dispatchers.IO) {
                    EncryptedTodoBackup.encode(snapshot, configuration.passphrase)
                }
                withContext(Dispatchers.IO) {
                    val output = getApplication<Application>().contentResolver.openOutputStream(
                        configuration.uri,
                        "w",
                    ) ?: error("Unable to open the automatic backup file")
                    output.bufferedWriter().use { writer -> writer.write(contents) }
                }
            }
        }
        _automaticBackupError.value = result.exceptionOrNull()?.message
        return result
    }

    private data class TodoState(
        val todos: List<Todo>,
        val settings: DueSettings,
    )

    private companion object {
        const val BACKUP_DEBOUNCE_MILLIS = 750L

        /** The midpoint of the 1..10 scale, used until a skill has an average to suggest. */
        const val DEFAULT_RATING = 5
    }
}
