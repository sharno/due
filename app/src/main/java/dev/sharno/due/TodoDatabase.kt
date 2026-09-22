package dev.sharno.due

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.migration.Migration
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "todos")
data class TodoEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val dueAtMillis: Long,
    val completed: Boolean,
    val recurrence: String?,
    val occurrencesCompleted: Int,
)

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY completed ASC, dueAtMillis ASC")
    fun observeAll(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos ORDER BY completed ASC, dueAtMillis ASC")
    suspend fun all(): List<TodoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: TodoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(todos: List<TodoEntity>)

    /**
     * Updates must not go through `@Insert(REPLACE)`. SQLite implements REPLACE as delete-then-insert
     * and those deletes fire `ON DELETE` foreign-key actions, so re-inserting a todo would silently
     * cascade away everything that references it.
     */
    @Update
    suspend fun update(todo: TodoEntity)

    @Query("SELECT * FROM todos WHERE id = :taskId")
    suspend fun byId(taskId: String): TodoEntity?

    @Query("DELETE FROM todos WHERE id = :taskId")
    suspend fun delete(taskId: String)

    @Query("DELETE FROM todos")
    suspend fun deleteAll()
}

@Database(
    entities = [
        TodoEntity::class,
        SkillEntity::class,
        TodoSkillEntity::class,
        TaskCompletionEntity::class,
        SkillRatingEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class DueDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao

    abstract fun skillDao(): SkillDao

    abstract fun todoSkillDao(): TodoSkillDao

    abstract fun taskCompletionDao(): TaskCompletionDao

    abstract fun skillRatingDao(): SkillRatingDao

    companion object {
        private const val DATABASE_NAME = "due.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN recurrence TEXT")
                db.execSQL("ALTER TABLE todos ADD COLUMN occurrencesCompleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3_STATEMENTS.forEach(db::execSQL)
            }
        }

        @Volatile
        private var instance: DueDatabase? = null

        fun get(context: Context): DueDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                DueDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(LegacyTodoMigration(context.applicationContext))
                .build()
                .also { instance = it }
        }
    }
}

/**
 * The v3 tables, copied verbatim from the schema Room generates at
 * `app/schemas/dev.sharno.due.DueDatabase/3.json`.
 *
 * Room validates the live database structurally against the compiled entities, and there is no
 * `fallbackToDestructiveMigration` here — a mismatch is a crash on launch for every existing user,
 * not a silent reset. `SchemaMigrationSqlTest` pins these strings against the committed schema.
 */
internal val MIGRATION_2_3_STATEMENTS: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `skills` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `nameKey` TEXT NOT NULL, `colorArgb` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, `archivedAtMillis` INTEGER, PRIMARY KEY(`id`))",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_skills_nameKey` ON `skills` (`nameKey`)",
    "CREATE TABLE IF NOT EXISTS `todo_skills` (`todoId` TEXT NOT NULL, `skillId` TEXT NOT NULL, PRIMARY KEY(`todoId`, `skillId`), FOREIGN KEY(`todoId`) REFERENCES `todos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`skillId`) REFERENCES `skills`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_todo_skills_skillId` ON `todo_skills` (`skillId`)",
    "CREATE TABLE IF NOT EXISTS `task_completions` (`id` TEXT NOT NULL, `todoId` TEXT NOT NULL, `taskTitle` TEXT NOT NULL, `occurrenceIndex` INTEGER NOT NULL, `completedAtMillis` INTEGER NOT NULL, `ratingPromptedAtMillis` INTEGER, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_task_completions_todoId` ON `task_completions` (`todoId`)",
    "CREATE INDEX IF NOT EXISTS `index_task_completions_completedAtMillis` ON `task_completions` (`completedAtMillis`)",
    "CREATE TABLE IF NOT EXISTS `skill_ratings` (`completionId` TEXT NOT NULL, `skillId` TEXT NOT NULL, `rating` INTEGER NOT NULL, `ratedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`completionId`, `skillId`), FOREIGN KEY(`completionId`) REFERENCES `task_completions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`skillId`) REFERENCES `skills`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_skill_ratings_skillId` ON `skill_ratings` (`skillId`)",
)

private class LegacyTodoMigration(private val context: Context) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        val preferences = context.getSharedPreferences(
            TodoRepository.LEGACY_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val rawTodos = preferences.getString(TodoRepository.LEGACY_TODOS_KEY, null) ?: return
        val todos = TodoBackup.decodeLegacy(rawTodos)

        db.beginTransaction()
        try {
            todos.forEach { todo ->
                db.insert(
                    "todos",
                    SQLiteDatabase.CONFLICT_REPLACE,
                    ContentValues().apply {
                        put("id", todo.id)
                        put("title", todo.title)
                        put("dueAtMillis", todo.dueAtMillis)
                        put("completed", if (todo.completed) 1 else 0)
                    },
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        check(preferences.edit().remove(TodoRepository.LEGACY_TODOS_KEY).commit()) {
            "Unable to finish todo storage migration"
        }
    }
}

fun Todo.toEntity(): TodoEntity = TodoEntity(
    id = id,
    title = title,
    dueAtMillis = dueAtMillis,
    completed = completed,
    recurrence = recurrence?.let(RecurrenceRuleCodec::encodeToString),
    occurrencesCompleted = occurrencesCompleted,
)

fun TodoEntity.toTodo(): Todo = Todo(
    id = id,
    title = title,
    dueAtMillis = dueAtMillis,
    completed = completed,
    recurrence = recurrence?.let(RecurrenceRuleCodec::decodeFromString),
    occurrencesCompleted = occurrencesCompleted,
)
