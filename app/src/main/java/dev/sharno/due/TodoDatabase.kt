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

    @Query("SELECT * FROM todos WHERE id = :taskId")
    suspend fun byId(taskId: String): TodoEntity?

    @Query("DELETE FROM todos WHERE id = :taskId")
    suspend fun delete(taskId: String)

    @Query("DELETE FROM todos")
    suspend fun deleteAll()
}

@Database(
    entities = [TodoEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class DueDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao

    companion object {
        private const val DATABASE_NAME = "due.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN recurrence TEXT")
                db.execSQL("ALTER TABLE todos ADD COLUMN occurrencesCompleted INTEGER NOT NULL DEFAULT 0")
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
                .addMigrations(MIGRATION_1_2)
                .addCallback(LegacyTodoMigration(context.applicationContext))
                .build()
                .also { instance = it }
        }
    }
}

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
