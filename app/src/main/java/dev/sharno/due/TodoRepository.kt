package dev.sharno.due

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TodoRepository(context: Context) {
    private val database = DueDatabase.get(context)
    private val todos = database.todoDao()
    private val links = database.todoSkillDao()
    private val completions = database.taskCompletionDao()
    private val skills = database.skillDao()
    private val ratings = database.skillRatingDao()

    fun observe(): Flow<List<Todo>> = todos.observeAll().map { values -> values.map(TodoEntity::toTodo) }

    suspend fun all(): List<Todo> = todos.all().map(TodoEntity::toTodo)

    suspend fun add(todo: Todo) {
        todos.insert(todo.toEntity())
    }

    suspend fun update(todo: Todo) {
        todos.update(todo.toEntity())
    }

    /**
     * The single funnel every completion passes through, including the one from the notification
     * action, which runs with no UI present.
     *
     * Returns the session it recorded so the caller can offer a rating sheet, or null when there is
     * nothing to rate. A session is only written for a task that actually carries skills: someone
     * who never uses the feature should not accumulate rows or grow their encrypted backup. The
     * trade-off is that attaching a skill later does not invent past sessions.
     */
    suspend fun setCompleted(
        taskId: String,
        completed: Boolean,
        source: CompletionSource = CompletionSource.APP,
        nowMillis: Long = System.currentTimeMillis(),
    ): TaskCompletion? = database.withTransaction {
        val current = todos.byId(taskId)?.toTodo() ?: return@withTransaction null
        if (completed) {
            if (current.completed) return@withTransaction null
            todos.update(current.completeAt(nowMillis).toEntity())
            if (links.skillCountForTodo(taskId) == 0) return@withTransaction null

            val completion = TaskCompletion.create(
                todo = current,
                nowMillis = nowMillis,
                // The app path is about to show a sheet; the notification path never will, so it
                // stays unprompted and surfaces later as a session waiting to be rated.
                promptedAtMillis = nowMillis.takeIf { source == CompletionSource.APP },
            )
            completions.insert(completion.toEntity())
            completion
        } else {
            val occurrencesCompleted = if (
                current.completed && current.recurrence != null && current.occurrencesCompleted > 0
            ) {
                current.occurrencesCompleted - 1
            } else {
                current.occurrencesCompleted
            }
            todos.update(
                current.copy(
                    completed = false,
                    occurrencesCompleted = occurrencesCompleted,
                ).toEntity(),
            )
            // Undo removes the session it created, ratings cascading with it. Keeping a rating for
            // a session the user says did not happen would skew the average and double-count on the
            // next completion.
            completions.deleteLatestForTodo(taskId)
            null
        }
    }

    suspend fun delete(taskId: String) {
        todos.delete(taskId)
    }

    /** The whole graph, read in one transaction so the pieces cannot disagree. */
    suspend fun snapshot(): DueSnapshot = database.withTransaction {
        DueSnapshot(
            todos = todos.all().map(TodoEntity::toTodo),
            skills = skills.all().map(SkillEntity::toSkill),
            taskSkills = links.all().map(TodoSkillEntity::toLink),
            completions = completions.all().map(TaskCompletionEntity::toCompletion),
            ratings = ratings.all().map(SkillRatingEntity::toRating),
        )
    }

    /**
     * Replaces the entire database with a backup.
     *
     * This used to be `todos.deleteAll()` plus an insert. With foreign keys in play that single
     * delete now cascades away every skill link in the database — including links to tasks the
     * import is about to restore under the very same ids. So children are deleted first and parents
     * are inserted first, all inside one transaction.
     *
     * Taking a [DueSnapshot] rather than a todo list is what makes a partial restore impossible to
     * write by accident.
     */
    suspend fun replaceAll(snapshot: DueSnapshot) {
        val clean = snapshot.prune()
        database.withTransaction {
            ratings.deleteAll()
            completions.deleteAll()
            links.deleteAll()
            skills.deleteAll()
            todos.deleteAll()

            todos.insertAll(clean.todos.map(Todo::toEntity))
            skills.insertAll(clean.skills.map(Skill::toEntity))
            links.insertAll(clean.taskSkills.map(TaskSkillLink::toEntity))
            completions.insertAll(clean.completions.map(TaskCompletion::toEntity))
            ratings.insertAll(clean.ratings.map(SkillRating::toEntity))
        }
    }

    internal companion object {
        const val LEGACY_PREFERENCES_NAME = "todos"
        const val LEGACY_TODOS_KEY = "items"
    }
}
