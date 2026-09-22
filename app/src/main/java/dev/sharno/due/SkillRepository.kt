package dev.sharno.due

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SkillRepository(context: Context) {
    private val database = DueDatabase.get(context)
    private val skills = database.skillDao()
    private val links = database.todoSkillDao()
    private val completions = database.taskCompletionDao()
    private val ratings = database.skillRatingDao()

    fun observeGraph(): Flow<SkillGraph> = combine(
        skills.observeAll(),
        links.observeAll(),
        completions.observeAll(),
        ratings.observeAll(),
    ) { skillRows, linkRows, completionRows, ratingRows ->
        SkillGraph(
            skills = skillRows.map(SkillEntity::toSkill),
            links = linkRows.map(TodoSkillEntity::toLink),
            completions = completionRows.map(TaskCompletionEntity::toCompletion),
            ratings = ratingRows.map(SkillRatingEntity::toRating),
        )
    }

    suspend fun graph(): SkillGraph = database.withTransaction {
        SkillGraph(
            skills = skills.all().map(SkillEntity::toSkill),
            links = links.all().map(TodoSkillEntity::toLink),
            completions = completions.all().map(TaskCompletionEntity::toCompletion),
            ratings = ratings.all().map(SkillRatingEntity::toRating),
        )
    }

    /**
     * Resolves an existing skill by its normalized name or creates one.
     *
     * Returning the match rather than throwing is what makes a type-to-create picker behave: the
     * unique index never reaches the UI as a constraint violation.
     */
    suspend fun getOrCreate(
        name: String,
        colorArgb: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): Skill = database.withTransaction {
        val key = Skill.normalizeName(name)
        skills.byNameKey(key)?.toSkill() ?: Skill.create(name, colorArgb, nowMillis).also {
            skills.insert(it.toEntity())
        }
    }

    /** Throws if another skill already uses the name. */
    suspend fun updateDetails(skillId: String, name: String, colorArgb: Int) {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "A skill needs a name" }
        val key = Skill.normalizeName(trimmed)
        database.withTransaction {
            val clash = skills.byNameKey(key)
            require(clash == null || clash.id == skillId) { "A skill called \"$trimmed\" already exists" }
            skills.updateDetails(skillId, trimmed, key, colorArgb)
        }
    }

    /**
     * Archiving hides a skill and detaches it from tasks while keeping its ratings, so a typo or a
     * dropped hobby never silently rewrites the history the stats are built from.
     */
    suspend fun archive(skillId: String, nowMillis: Long = System.currentTimeMillis()) {
        database.withTransaction {
            links.deleteForSkill(skillId)
            skills.setArchived(skillId, nowMillis)
        }
    }

    suspend fun restore(skillId: String) {
        skills.setArchived(skillId, null)
    }

    suspend fun ratingCount(skillId: String): Int = ratings.countForSkill(skillId)

    suspend fun taskCount(skillId: String): Int = links.taskCountForSkill(skillId)

    /** Permanent deletion, which cascades the skill's ratings. Confirm the count first. */
    suspend fun deletePermanently(skillId: String) {
        skills.delete(skillId)
    }

    suspend fun skillIdsForTask(todoId: String): Set<String> =
        links.skillIdsForTodo(todoId).toSet()

    suspend fun setSkillsForTask(todoId: String, skillIds: Set<String>) {
        database.withTransaction {
            links.deleteForTodo(todoId)
            links.insertAll(skillIds.map { TodoSkillEntity(todoId = todoId, skillId = it) })
        }
    }

    suspend fun rate(
        completionId: String,
        ratingsBySkillId: Map<String, Int>,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (ratingsBySkillId.isEmpty()) return
        ratings.insertAll(
            ratingsBySkillId.map { (skillId, rating) ->
                SkillRating(completionId, skillId, rating, nowMillis).toEntity()
            },
        )
    }

    /** Removes a completion from the unrated list without inventing a rating for it. */
    suspend fun dismissRatingPrompt(
        completionId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        completions.markPrompted(completionId, nowMillis)
    }
}
