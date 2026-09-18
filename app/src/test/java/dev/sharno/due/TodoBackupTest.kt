package dev.sharno.due

import org.junit.Assert.assertEquals
import org.junit.Test

class TodoBackupTest {
    @Test
    fun roundTripPreservesTodos() {
        val todos = listOf(
            Todo("one", "Buy milk", 1_700_000_000_000, false),
            Todo("two", "Read book", 1_700_000_001_000, true),
        )

        assertEquals(todos, TodoBackup.decode(TodoBackup.encode(todos)))
    }

    @Test
    fun legacyArrayRemainsReadableForMigration() {
        val legacy = """
            [{"id":"one","title":"Buy milk","dueAtMillis":1700000000000,"completed":false}]
        """.trimIndent()

        assertEquals(
            listOf(Todo("one", "Buy milk", 1_700_000_000_000, false)),
            TodoBackup.decodeLegacy(legacy),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBackupsFromAnotherFormat() {
        TodoBackup.decode("{\"format\":\"not-due\",\"version\":1,\"todos\":[]}")
    }
}
