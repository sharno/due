package dev.sharno.due

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the 2 → 3 migration without needing an emulator.
 *
 * Room validates the live database structurally against the compiled entities, and this project has
 * no `fallbackToDestructiveMigration`, so a column, index or foreign key drifting out of step with
 * the entities is a crash on launch for every existing user. The exported schema is the compiler's
 * own description of those entities, so comparing the migration against it catches that drift with
 * nothing but the `org.json` dependency the tests already have.
 */
class SchemaMigrationSqlTest {
    private val schemaDirectory: File
        get() {
            var candidate: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
            while (candidate != null) {
                val schemas = File(candidate, "schemas/dev.sharno.due.DueDatabase")
                if (schemas.isDirectory) return schemas
                val nested = File(candidate, "app/schemas/dev.sharno.due.DueDatabase")
                if (nested.isDirectory) return nested
                candidate = candidate.parentFile
            }
            throw AssertionError("Exported Room schemas not found; is app/schemas committed?")
        }

    private fun schema(version: Int): JSONObject {
        val file = File(schemaDirectory, "$version.json")
        assertTrue("Schema $version.json is missing — it must be committed alongside the migration", file.isFile)
        return JSONObject(file.readText()).getJSONObject("database")
    }

    private fun tableNames(version: Int): Set<String> {
        val entities = schema(version).getJSONArray("entities")
        return (0 until entities.length())
            .map { entities.getJSONObject(it).getString("tableName") }
            .toSet()
    }

    private fun normalize(sql: String) = sql.replace(Regex("\\s+"), " ").trim()

    @Test
    fun theExportedSchemaIsAtVersionThree() {
        assertEquals(3, schema(3).getInt("version"))
    }

    @Test
    fun everyNewTableAndIndexIsCreatedByTheMigration() {
        val alreadyThere = tableNames(2)
        val statements = MIGRATION_2_3_STATEMENTS.map(::normalize)
        val entities = schema(3).getJSONArray("entities")

        var checked = 0
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val table = entity.getString("tableName")
            if (table in alreadyThere) continue

            val createTable = normalize(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            assertTrue(
                "Migration 2->3 does not create `$table` exactly as the entity declares it.\nExpected: $createTable",
                createTable in statements,
            )
            checked++

            val indices = entity.optJSONArray("indices") ?: continue
            for (i in 0 until indices.length()) {
                val createIndex = normalize(
                    indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table),
                )
                assertTrue(
                    "Migration 2->3 is missing an index on `$table`.\nExpected: $createIndex",
                    createIndex in statements,
                )
                checked++
            }
        }

        assertTrue("No new tables were checked; the schema comparison is not doing anything", checked > 0)
        assertEquals(
            "The migration runs statements that the schema does not describe",
            checked,
            statements.size,
        )
    }

    @Test
    fun theMigrationOnlyAddsTablesAndNeverTouchesExistingData() {
        for (statement in MIGRATION_2_3_STATEMENTS) {
            val upper = statement.uppercase()
            assertTrue(
                "Migration 2->3 should only create tables and indices, but runs: $statement",
                upper.startsWith("CREATE TABLE") || upper.startsWith("CREATE INDEX") ||
                    upper.startsWith("CREATE UNIQUE INDEX"),
            )
        }
    }

    @Test
    fun theCompletionLogHasNoForeignKeyToTodos() {
        val entities = schema(3).getJSONArray("entities")
        val completions = (0 until entities.length())
            .map { entities.getJSONObject(it) }
            .single { it.getString("tableName") == "task_completions" }
        val foreignKeys = completions.optJSONArray("foreignKeys")

        // Rating history must outlive the task it belongs to, and must survive the wholesale delete
        // an import performs. A foreign key here — even ON DELETE SET NULL — would break both.
        assertTrue(
            "task_completions must not reference todos, or deleting a task would take its ratings with it",
            foreignKeys == null || foreignKeys.length() == 0,
        )
    }
}
