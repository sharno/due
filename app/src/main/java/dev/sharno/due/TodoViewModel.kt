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
import kotlinx.coroutines.withContext

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TodoRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val _todos = MutableStateFlow<List<Todo>>(emptyList())
    val todos = _todos.asStateFlow()
    private val _settings = MutableStateFlow(DueSettings())
    val settings = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observe()
                .combine(settingsRepository.settings, ::TodoState)
                .collect { state ->
                    _todos.value = state.todos
                    _settings.value = state.settings
                    TaskScheduler.synchronize(application, state.todos, state.settings)
                }
        }
    }

    fun add(title: String, dueAtMillis: Long) {
        require(title.isNotBlank()) { "A todo needs a title" }
        viewModelScope.launch {
            repository.add(Todo.create(title, dueAtMillis))
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

    private data class TodoState(
        val todos: List<Todo>,
        val settings: DueSettings,
    )
}
