package dev.sharno.due

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "skills",
    indices = [Index(value = ["nameKey"], unique = true)],
)
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameKey: String,
    val colorArgb: Int,
    val createdAtMillis: Long,
    val archivedAtMillis: Long?,
)

@Entity(
    tableName = "todo_skills",
    primaryKeys = ["todoId", "skillId"],
    indices = [Index(value = ["skillId"])],
    foreignKeys = [
        ForeignKey(
            entity = TodoEntity::class,
            parentColumns = ["id"],
            childColumns = ["todoId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SkillEntity::class,
            parentColumns = ["id"],
            childColumns = ["skillId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TodoSkillEntity(
    val todoId: String,
    val skillId: String,
)

/**
 * One completion event.
 *
 * Deliberately has **no** foreign key to `todos`: a `ON DELETE SET NULL` would fire on every
 * `INSERT OR REPLACE` into todos and on the wholesale delete an import performs, wiping the link
 * for rows the import is about to restore under the very same ids.
 */
@Entity(
    tableName = "task_completions",
    indices = [Index(value = ["todoId"]), Index(value = ["completedAtMillis"])],
)
data class TaskCompletionEntity(
    @PrimaryKey val id: String,
    val todoId: String,
    val taskTitle: String,
    val occurrenceIndex: Int,
    val completedAtMillis: Long,
    val ratingPromptedAtMillis: Long?,
)

@Entity(
    tableName = "skill_ratings",
    primaryKeys = ["completionId", "skillId"],
    indices = [Index(value = ["skillId"])],
    foreignKeys = [
        ForeignKey(
            entity = TaskCompletionEntity::class,
            parentColumns = ["id"],
            childColumns = ["completionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SkillEntity::class,
            parentColumns = ["id"],
            childColumns = ["skillId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SkillRatingEntity(
    val completionId: String,
    val skillId: String,
    val rating: Int,
    val ratedAtMillis: Long,
)

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills ORDER BY name COLLATE NOCASE ASC")
    suspend fun all(): List<SkillEntity>

    @Query("SELECT * FROM skills WHERE nameKey = :nameKey")
    suspend fun byNameKey(nameKey: String): SkillEntity?

    // Never REPLACE: `skills` is a foreign-key parent, and REPLACE deletes before inserting, which
    // would cascade away every rating the skill has ever collected.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(skill: SkillEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(skills: List<SkillEntity>)

    @Query("UPDATE skills SET name = :name, nameKey = :nameKey, colorArgb = :colorArgb WHERE id = :skillId")
    suspend fun updateDetails(skillId: String, name: String, nameKey: String, colorArgb: Int)

    @Query("UPDATE skills SET archivedAtMillis = :archivedAtMillis WHERE id = :skillId")
    suspend fun setArchived(skillId: String, archivedAtMillis: Long?)

    @Query("DELETE FROM skills WHERE id = :skillId")
    suspend fun delete(skillId: String)

    @Query("DELETE FROM skills")
    suspend fun deleteAll()
}

@Dao
interface TodoSkillDao {
    @Query("SELECT * FROM todo_skills")
    fun observeAll(): Flow<List<TodoSkillEntity>>

    @Query("SELECT * FROM todo_skills")
    suspend fun all(): List<TodoSkillEntity>

    @Query("SELECT COUNT(*) FROM todo_skills WHERE todoId = :todoId")
    suspend fun skillCountForTodo(todoId: String): Int

    @Query("SELECT skillId FROM todo_skills WHERE todoId = :todoId")
    suspend fun skillIdsForTodo(todoId: String): List<String>

    @Query("SELECT COUNT(DISTINCT todoId) FROM todo_skills WHERE skillId = :skillId")
    suspend fun taskCountForSkill(skillId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(links: List<TodoSkillEntity>)

    @Query("DELETE FROM todo_skills WHERE todoId = :todoId")
    suspend fun deleteForTodo(todoId: String)

    @Query("DELETE FROM todo_skills WHERE skillId = :skillId")
    suspend fun deleteForSkill(skillId: String)

    @Query("DELETE FROM todo_skills")
    suspend fun deleteAll()
}

@Dao
interface TaskCompletionDao {
    @Query("SELECT * FROM task_completions ORDER BY completedAtMillis DESC")
    fun observeAll(): Flow<List<TaskCompletionEntity>>

    @Query("SELECT * FROM task_completions ORDER BY completedAtMillis DESC")
    suspend fun all(): List<TaskCompletionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(completion: TaskCompletionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(completions: List<TaskCompletionEntity>)

    @Query("UPDATE task_completions SET ratingPromptedAtMillis = :promptedAtMillis WHERE id = :completionId")
    suspend fun markPrompted(completionId: String, promptedAtMillis: Long)

    /**
     * Undoing a completion removes the session it created, ratings cascading with it.
     *
     * Android's SQLite is not built with `SQLITE_ENABLE_UPDATE_DELETE_LIMIT`, so `DELETE ... LIMIT`
     * is a syntax error and the row has to be selected in a subquery.
     */
    @Query(
        """
        DELETE FROM task_completions
        WHERE id = (
            SELECT id FROM task_completions
            WHERE todoId = :todoId
            ORDER BY completedAtMillis DESC, id DESC
            LIMIT 1
        )
        """,
    )
    suspend fun deleteLatestForTodo(todoId: String)

    @Query("DELETE FROM task_completions")
    suspend fun deleteAll()
}

@Dao
interface SkillRatingDao {
    @Query("SELECT * FROM skill_ratings")
    fun observeAll(): Flow<List<SkillRatingEntity>>

    @Query("SELECT * FROM skill_ratings")
    suspend fun all(): List<SkillRatingEntity>

    @Query("SELECT COUNT(*) FROM skill_ratings WHERE skillId = :skillId")
    suspend fun countForSkill(skillId: String): Int

    // Safe to REPLACE: skill_ratings is a leaf, nothing references it.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(ratings: List<SkillRatingEntity>)

    @Query("DELETE FROM skill_ratings")
    suspend fun deleteAll()
}

fun Skill.toEntity(): SkillEntity = SkillEntity(
    id = id,
    name = name,
    nameKey = nameKey,
    colorArgb = colorArgb,
    createdAtMillis = createdAtMillis,
    archivedAtMillis = archivedAtMillis,
)

fun SkillEntity.toSkill(): Skill = Skill(
    id = id,
    name = name,
    colorArgb = colorArgb,
    createdAtMillis = createdAtMillis,
    archivedAtMillis = archivedAtMillis,
)

fun TaskSkillLink.toEntity(): TodoSkillEntity = TodoSkillEntity(todoId = todoId, skillId = skillId)

fun TodoSkillEntity.toLink(): TaskSkillLink = TaskSkillLink(todoId = todoId, skillId = skillId)

fun TaskCompletion.toEntity(): TaskCompletionEntity = TaskCompletionEntity(
    id = id,
    todoId = todoId,
    taskTitle = taskTitle,
    occurrenceIndex = occurrenceIndex,
    completedAtMillis = completedAtMillis,
    ratingPromptedAtMillis = ratingPromptedAtMillis,
)

fun TaskCompletionEntity.toCompletion(): TaskCompletion = TaskCompletion(
    id = id,
    todoId = todoId,
    taskTitle = taskTitle,
    occurrenceIndex = occurrenceIndex,
    completedAtMillis = completedAtMillis,
    ratingPromptedAtMillis = ratingPromptedAtMillis,
)

fun SkillRating.toEntity(): SkillRatingEntity = SkillRatingEntity(
    completionId = completionId,
    skillId = skillId,
    rating = rating,
    ratedAtMillis = ratedAtMillis,
)

fun SkillRatingEntity.toRating(): SkillRating = SkillRating(
    completionId = completionId,
    skillId = skillId,
    rating = rating,
    ratedAtMillis = ratedAtMillis,
)
