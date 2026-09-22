package dev.sharno.due

/**
 * Everything a backup carries.
 *
 * Export, import and the encrypted automatic backup all move whole snapshots rather than bare todo
 * lists. That is deliberate: once tasks, skills and ratings reference each other, a backup that
 * carries only part of the graph restores as data loss, and the type makes a partial restore a
 * compile error instead of a silent one.
 */
data class DueSnapshot(
    val todos: List<Todo> = emptyList(),
    val skills: List<Skill> = emptyList(),
    val taskSkills: List<TaskSkillLink> = emptyList(),
    val completions: List<TaskCompletion> = emptyList(),
    val ratings: List<SkillRating> = emptyList(),
) {
    /**
     * Drops rows whose parents are missing, so a hand-edited or truncated backup cannot abort the
     * whole restore with a raw constraint violation.
     *
     * Completions are never dropped for an unknown task: that is the normal state for a task the
     * user deleted, and keeping the id means the link repairs itself if that task is ever restored.
     */
    fun prune(): DueSnapshot {
        val todoIds = todos.mapTo(mutableSetOf(), Todo::id)
        val skillIds = skills.mapTo(mutableSetOf(), Skill::id)

        val prunedCompletions = completions.distinctBy(TaskCompletion::id)
        val completionIds = prunedCompletions.mapTo(mutableSetOf(), TaskCompletion::id)

        return copy(
            todos = todos.distinctBy(Todo::id),
            skills = skills.distinctBy(Skill::id),
            taskSkills = taskSkills
                .filter { it.todoId in todoIds && it.skillId in skillIds }
                .distinct(),
            completions = prunedCompletions,
            ratings = ratings
                .filter { it.completionId in completionIds && it.skillId in skillIds }
                .distinctBy { it.completionId to it.skillId },
        )
    }
}
