package dev.sharno.due

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TodoRepository(context: Context) {
    private val database = DueDatabase.get(context)
    private val todos = database.todoDao()

    fun observe(): Flow<List<Todo>> = todos.observeAll().map { values -> values.map(TodoEntity::toTodo) }

    suspend fun all(): List<Todo> = todos.all().map(TodoEntity::toTodo)

    suspend fun add(todo: Todo) {
        todos.insert(todo.toEntity())
    }

    suspend fun setCompleted(taskId: String, completed: Boolean) {
        database.withTransaction {
            val current = todos.byId(taskId)?.toTodo() ?: return@withTransaction
            if (completed) {
                if (!current.completed) {
                    todos.insert(current.completeAt(System.currentTimeMillis()).toEntity())
                }
            } else {
                val occurrencesCompleted = if (
                    current.completed && current.recurrence != null && current.occurrencesCompleted > 0
                ) {
                    current.occurrencesCompleted - 1
                } else {
                    current.occurrencesCompleted
                }
                todos.insert(
                    current.copy(
                        completed = false,
                        occurrencesCompleted = occurrencesCompleted,
                    ).toEntity(),
                )
            }
        }
    }

    suspend fun delete(taskId: String) {
        todos.delete(taskId)
    }

    suspend fun replaceAll(values: List<Todo>) {
        database.withTransaction {
            todos.deleteAll()
            todos.insertAll(values.map(Todo::toEntity))
        }
    }

    internal companion object {
        const val LEGACY_PREFERENCES_NAME = "todos"
        const val LEGACY_TODOS_KEY = "items"
    }
}
