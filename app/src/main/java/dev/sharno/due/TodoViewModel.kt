package dev.sharno.due

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TodoRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val _todos = MutableStateFlow<List<Todo>>(emptyList())
    val todos = _todos.asStateFlow()
    private val _settings = MutableStateFlow(DueSettings())
    val settings = _settings.asStateFlow()
    private val _automaticBackupError = MutableStateFlow<String?>(null)
    val automaticBackupError = _automaticBackupError.asStateFlow()
    private val automaticBackupMutex = Mutex()

    init {
        viewModelScope.launch {
            repository.observe()
                .combine(settingsRepository.settings, ::TodoState)
                .collect { state ->
                    _todos.value = state.todos
                    _settings.value = state.settings
                    TaskScheduler.synchronize(application, state.todos, state.settings)
                    if (state.settings.automaticBackupConfigured) {
                        writeAutomaticBackup(state.todos)
                    } else {
                        _automaticBackupError.value = null
                    }
                }
        }
    }

    fun add(title: String, dueAtMillis: Long, recurrence: RecurrenceRule?) {
        require(title.isNotBlank()) { "A todo needs a title" }
        viewModelScope.launch {
            repository.add(Todo.create(title, dueAtMillis, recurrence))
        }
    }

    fun setCompleted(todo: Todo, completed: Boolean) {
        viewModelScope.launch {
            repository.setCompleted(todo.id, completed)
        }
    }

    fun delete(todo: Todo) {
        viewModelScope.launch {
            repository.delete(todo.id)
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
                val todos = withContext(Dispatchers.IO) { repository.all() }
                writeAutomaticBackup(todos).getOrThrow()
            }
            if (result.isFailure) {
                _automaticBackupError.value = result.exceptionOrNull()?.message
            }
            onResult(result)
        }
    }

    fun backupNow(onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val todos = withContext(Dispatchers.IO) { repository.all() }
            val result = writeAutomaticBackup(todos)
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
                val contents = TodoBackup.encode(repository.all())
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
                val todos = withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: error("Unable to open the selected file")
                    input.bufferedReader().use { reader -> TodoBackup.decode(reader.readText()) }
                }
                repository.replaceAll(todos)
            }
            onResult(result)
        }
    }

    fun restoreEncrypted(uri: Uri, passphrase: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val todos = withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: error("Unable to open the selected file")
                    input.bufferedReader().use { reader ->
                        EncryptedTodoBackup.decode(reader.readText(), passphrase)
                    }
                }
                repository.replaceAll(todos)
            }
            onResult(result)
        }
    }

    private suspend fun writeAutomaticBackup(todos: List<Todo>): Result<Unit> {
        val result = runCatching {
            automaticBackupMutex.withLock {
                val configuration = withContext(Dispatchers.IO) {
                    settingsRepository.automaticBackupConfiguration()
                } ?: error("Automatic backup is not configured")
                val contents = withContext(Dispatchers.IO) {
                    EncryptedTodoBackup.encode(todos, configuration.passphrase)
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
}
