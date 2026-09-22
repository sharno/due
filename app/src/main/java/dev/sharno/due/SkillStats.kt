package dev.sharno.due

/** Everything the skills feature knows, as one consistent snapshot. */
data class SkillGraph(
    val skills: List<Skill> = emptyList(),
    val links: List<TaskSkillLink> = emptyList(),
    val completions: List<TaskCompletion> = emptyList(),
    val ratings: List<SkillRating> = emptyList(),
) {
    val activeSkills: List<Skill> get() = skills.filterNot(Skill::archived)

    fun skillsByTodoId(): Map<String, List<Skill>> {
        val byId = skills.associateBy(Skill::id)
        return links
            .groupBy(TaskSkillLink::todoId)
            .mapValues { (_, links) -> links.mapNotNull { byId[it.skillId] }.sortedBy { it.name } }
    }

    /** Completions that were never offered a rating sheet, newest first. */
    fun unratedCompletions(): List<TaskCompletion> {
        val rated = ratings.mapTo(mutableSetOf(), SkillRating::completionId)
        return completions
            .filter { it.ratingPromptedAtMillis == null && it.id !in rated }
            .sortedByDescending(TaskCompletion::completedAtMillis)
    }

    fun ratingsByTodoId(): Map<String, List<TaskRatingSummary>> {
        val byCompletion = ratings.groupBy(SkillRating::completionId)
        return completions
            .mapNotNull { completion ->
                val forCompletion = byCompletion[completion.id] ?: return@mapNotNull null
                completion.todoId to TaskRatingSummary(
                    completedAtMillis = completion.completedAtMillis,
                    ratings = forCompletion.associate { it.skillId to it.rating },
                )
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, summaries) -> summaries.sortedByDescending(TaskRatingSummary::completedAtMillis) }
    }
}

data class TaskRatingSummary(
    val completedAtMillis: Long,
    val ratings: Map<String, Int>,
)

data class SkillStats(
    val skill: Skill,
    val ratedSessions: Int,
    val unratedSessions: Int,
    val average: Double?,
    val best: Int?,
    val worst: Int?,
    val lastPractisedAtMillis: Long?,
    /** The most recent ratings, oldest first, for the sparkline. */
    val recent: List<Int>,
    /** Mean of the last few ratings minus the few before them; null until there is enough history. */
    val trend: Double?,
    val taskCount: Int,
)

/**
 * Skill statistics.
 *
 * Computed in Kotlin rather than SQL on purpose. minSdk 26 ships SQLite 3.19, which has no window
 * functions (they arrive in 3.25 / API 30), so trends would degrade into correlated subqueries; the
 * dataset is tiny; and this project's only test harness is the JVM one, where pure functions over
 * three lists can actually be covered.
 */
object SkillStatsCalculator {
    private const val TREND_WINDOW = 3
    private const val RECENT_LIMIT = 20

    fun summarise(graph: SkillGraph): List<SkillStats> {
        // A rating is positioned by when its session happened, not when it was typed in: a session
        // rated a day late still belongs at its own place in the timeline.
        val completionTimes = graph.completions.associate { it.id to it.completedAtMillis }
        val ratingsBySkill = graph.ratings.groupBy(SkillRating::skillId)
        val todosBySkill = graph.links.groupBy(TaskSkillLink::skillId) { it.todoId }
        val completionsByTodo = graph.completions.groupBy(TaskCompletion::todoId)
        val ratedCompletionsBySkill = graph.ratings
            .groupBy(SkillRating::skillId) { it.completionId }
            .mapValues { (_, ids) -> ids.toSet() }

        return graph.skills.map { skill ->
            val skillRatings = ratingsBySkill[skill.id]
                .orEmpty()
                .sortedBy { completionTimes[it.completionId] ?: it.ratedAtMillis }
            val values = skillRatings.map(SkillRating::rating)
            val linkedTodoIds = todosBySkill[skill.id].orEmpty().toSet()
            val ratedIds = ratedCompletionsBySkill[skill.id].orEmpty()
            val unrated = linkedTodoIds
                .flatMap { completionsByTodo[it].orEmpty() }
                .count { it.id !in ratedIds }

            SkillStats(
                skill = skill,
                ratedSessions = values.size,
                unratedSessions = unrated,
                average = values.takeIf { it.isNotEmpty() }?.average(),
                best = values.maxOrNull(),
                worst = values.minOrNull(),
                lastPractisedAtMillis = skillRatings
                    .maxOfOrNull { completionTimes[it.completionId] ?: it.ratedAtMillis },
                recent = values.takeLast(RECENT_LIMIT),
                trend = trendOf(values),
                taskCount = linkedTodoIds.size,
            )
        }
    }

    /** Positive means improving. Null until there are at least two windows to compare. */
    private fun trendOf(values: List<Int>): Double? {
        if (values.size < TREND_WINDOW * 2) return null
        val recent = values.takeLast(TREND_WINDOW)
        val previous = values.dropLast(TREND_WINDOW).takeLast(TREND_WINDOW)
        return recent.average() - previous.average()
    }
}
