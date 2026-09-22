package dev.sharno.due

import java.util.Locale
import java.util.UUID

/** Something the user is practising, attached to any number of tasks. */
data class Skill(
    val id: String,
    val name: String,
    val colorArgb: Int,
    val createdAtMillis: Long,
    val archivedAtMillis: Long? = null,
) {
    init {
        require(name.isNotBlank()) { "A skill needs a name" }
    }

    val archived: Boolean get() = archivedAtMillis != null

    val nameKey: String get() = normalizeName(name)

    companion object {
        private val WHITESPACE = Regex("\\s+")

        fun create(name: String, colorArgb: Int, nowMillis: Long): Skill = Skill(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            colorArgb = colorArgb,
            createdAtMillis = nowMillis,
        )

        /**
         * The key used to keep skill names unique.
         *
         * Not SQLite's `COLLATE NOCASE`, which folds ASCII only and would happily store both "CAFÉ"
         * and "café". `Locale.ROOT` avoids the Turkish dotted-I trap, where a device locale would
         * otherwise lower-case "I" to "ı" and make the key device-dependent.
         */
        fun normalizeName(name: String): String =
            name.trim().lowercase(Locale.ROOT).replace(WHITESPACE, " ")
    }
}

/**
 * One "mark done" event.
 *
 * This is the only record that an occurrence happened: a recurring task just rolls its due date
 * forward and bumps a counter, and nothing else in the app stores a completion time.
 *
 * [todoId] deliberately has no foreign key to `todos`, so deleting a task leaves its history intact
 * with an id that simply matches nothing — and re-links itself if that task is ever restored from a
 * backup, since backups round-trip the original ids. [taskTitle] is a snapshot so history stays
 * readable once the task is gone.
 */
data class TaskCompletion(
    val id: String,
    val todoId: String,
    val taskTitle: String,
    val occurrenceIndex: Int,
    val completedAtMillis: Long,
    val ratingPromptedAtMillis: Long? = null,
) {
    companion object {
        fun create(
            todo: Todo,
            nowMillis: Long,
            promptedAtMillis: Long?,
        ): TaskCompletion = TaskCompletion(
            id = UUID.randomUUID().toString(),
            todoId = todo.id,
            taskTitle = todo.title,
            occurrenceIndex = todo.occurrencesCompleted,
            completedAtMillis = nowMillis,
            ratingPromptedAtMillis = promptedAtMillis,
        )
    }
}

data class SkillRating(
    val completionId: String,
    val skillId: String,
    val rating: Int,
    val ratedAtMillis: Long,
) {
    init {
        require(rating in RATING_RANGE) { "A rating must be between 1 and 10" }
    }

    companion object {
        val RATING_RANGE = 1..10
    }
}

/**
 * Where a completion came from.
 *
 * A completion from the notification action happens with no UI present, so it is recorded without a
 * prompt timestamp and surfaces later as an unrated session instead of being lost.
 */
enum class CompletionSource {
    APP,
    NOTIFICATION,
}

/** A task and the skills attached to it. */
data class TaskSkillLink(val todoId: String, val skillId: String)
