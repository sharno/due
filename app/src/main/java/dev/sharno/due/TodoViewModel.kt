package dev.sharno.due

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TodoRepository(application)
    private val _todos = MutableStateFlow(repository.all().sortedForDisplay())
    val todos = _todos.asStateFlow()

    init {
        TaskScheduler.synchronize(application, _todos.value)
    }

    fun add(title: String, dueAtMillis: Long) {
        require(title.isNotBlank()) { "A todo needs a title" }
        persist(_todos.value + Todo.create(title, dueAtMillis))
    }

    fun setCompleted(todo: Todo, completed: Boolean) {
        persist(_todos.value.map { current ->
            if (current.id == todo.id) current.copy(completed = completed) else current
        })
    }

    fun delete(todo: Todo) {
        TaskScheduler.cancelAlarm(getApplication(), todo.id)
        persist(_todos.value.filterNot { it.id == todo.id })
    }

    private fun persist(todos: List<Todo>) {
        val sorted = todos.sortedForDisplay()
        repository.save(sorted)
        _todos.value = sorted
        TaskScheduler.synchronize(getApplication(), sorted)
    }

    private fun List<Todo>.sortedForDisplay(): List<Todo> = sortedWith(
        compareBy<Todo> { it.completed }.thenBy { it.dueAtMillis },
    )
}
