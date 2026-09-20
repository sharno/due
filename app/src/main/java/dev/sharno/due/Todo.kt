package dev.sharno.due

import java.util.UUID

data class Todo(
    val id: String,
    val title: String,
    val dueAtMillis: Long,
    val completed: Boolean,
    val recurrence: RecurrenceRule? = null,
    val occurrencesCompleted: Int = 0,
) {
    init {
        require(occurrencesCompleted >= 0) { "Completed occurrences cannot be negative" }
        require(recurrence != null || occurrencesCompleted == 0) {
            "A non-recurring todo cannot have completed occurrences"
        }
    }

    companion object {
        fun create(
            title: String,
            dueAtMillis: Long,
            recurrence: RecurrenceRule? = null,
        ): Todo = Todo(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            dueAtMillis = dueAtMillis,
            completed = false,
            recurrence = recurrence,
        )
    }
}
