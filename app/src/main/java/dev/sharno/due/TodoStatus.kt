package dev.sharno.due

/**
 * A task's state at a given moment.
 *
 * Derived rather than stored: [Todo] only records `completed`, and whether a task is overdue depends
 * on the wall clock, which changes with no database write to react to.
 */
enum class TodoStatus {
    TODO,
    OVERDUE,
    DONE,
}

fun Todo.statusAt(nowMillis: Long): TodoStatus = when {
    completed -> TodoStatus.DONE
    dueAtMillis <= nowMillis -> TodoStatus.OVERDUE
    else -> TodoStatus.TODO
}

/** The task-list filter. Defaults to [TODO], which is everything still outstanding. */
enum class TaskFilter(val label: String) {
    TODO("To do"),
    OVERDUE("Overdue"),
    DONE("Done"),
    ALL("All");

    /**
     * [TODO] deliberately includes overdue tasks: "not yet done" is what the filter means, and a
     * default view that hid the most urgent tasks would be a bug. [OVERDUE] narrows it further.
     *
     * Recurring tasks need no special case. Completing one rolls its due date forward and leaves
     * `completed` false, so it moves from OVERDUE back to TODO and stays visible here; it only
     * reaches DONE once its recurrence rule genuinely ends.
     */
    fun matches(status: TodoStatus): Boolean = when (this) {
        TODO -> status != TodoStatus.DONE
        OVERDUE -> status == TodoStatus.OVERDUE
        DONE -> status == TodoStatus.DONE
        ALL -> true
    }
}
