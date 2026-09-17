package dev.sharno.due

import java.util.UUID

data class Todo(
    val id: String,
    val title: String,
    val dueAtMillis: Long,
    val completed: Boolean,
) {
    companion object {
        fun create(title: String, dueAtMillis: Long): Todo = Todo(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            dueAtMillis = dueAtMillis,
            completed = false,
        )
    }
}
