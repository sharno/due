package dev.sharno.due

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class TodoBackupTest {
    @Test
    fun roundTripPreservesTodos() {
        val todos = listOf(
            Todo(
                id = "one",
                title = "Buy milk",
                dueAtMillis = 1_700_000_000_000,
                completed = false,
                recurrence = RecurrenceRule(
                    frequency = RecurrenceFrequency.WEEKLY,
                    daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                    end = RecurrenceEnd.On(LocalDate.of(2027, 1, 1)),
                ),
            ),
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

    @Test
    fun oldBackupsDefaultToNonRepeating() {
        val backup = """
            {"format":"dev.sharno.due.todos","version":1,"todos":[{"id":"one","title":"Buy milk","dueAtMillis":1700000000000,"completed":false}]}
        """.trimIndent()

        assertEquals(null, TodoBackup.decode(backup).single().recurrence)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBackupsFromAnotherFormat() {
        TodoBackup.decode("{\"format\":\"not-due\",\"version\":1,\"todos\":[]}")
    }
}
