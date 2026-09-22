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

        val snapshot = DueSnapshot(todos = todos)
        assertEquals(snapshot, TodoBackup.decodeSnapshot(TodoBackup.encode(snapshot)))
        assertEquals(todos, TodoBackup.decode(TodoBackup.encode(snapshot)))
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

    @Test
    fun roundTripPreservesSkillsLinksAndRatings() {
        val snapshot = DueSnapshot(
            todos = listOf(Todo("one", "Practise scales", 1_700_000_000_000, false)),
            skills = listOf(
                Skill("guitar", "Guitar", 0xFFE65100.toInt(), 10),
                Skill("focus", "Focus", 0xFF1565C0.toInt(), 20, archivedAtMillis = 30),
            ),
            taskSkills = listOf(TaskSkillLink("one", "guitar"), TaskSkillLink("one", "focus")),
            completions = listOf(
                TaskCompletion("c1", "one", "Practise scales", 0, 1_700_000_100_000, 1_700_000_100_000),
                // A session from the notification action, never offered a rating sheet.
                TaskCompletion("c2", "one", "Practise scales", 1, 1_700_000_200_000, null),
            ),
            ratings = listOf(
                SkillRating("c1", "guitar", 7, 1_700_000_101_000),
                SkillRating("c1", "focus", 4, 1_700_000_101_000),
            ),
        )

        assertEquals(snapshot, TodoBackup.decodeSnapshot(TodoBackup.encode(snapshot)))
    }

    /** History outlives its task, so a completion whose todo is gone must survive a round trip. */
    @Test
    fun aCompletionWhoseTaskWasDeletedSurvives() {
        val snapshot = DueSnapshot(
            skills = listOf(Skill("guitar", "Guitar", 0xFFE65100.toInt(), 10)),
            completions = listOf(TaskCompletion("c1", "deleted", "Practise scales", 0, 1_700_000_100_000, null)),
            ratings = listOf(SkillRating("c1", "guitar", 9, 1_700_000_101_000)),
        )

        assertEquals(snapshot, TodoBackup.decodeSnapshot(TodoBackup.encode(snapshot)))
    }

    @Test
    fun backupsWrittenBeforeSkillsExistedStillLoad() {
        val backup = """
            {"format":"dev.sharno.due.todos","version":1,"todos":[{"id":"one","title":"Buy milk","dueAtMillis":1700000000000,"completed":false}]}
        """.trimIndent()

        val snapshot = TodoBackup.decodeSnapshot(backup)
        assertEquals(1, snapshot.todos.size)
        assertEquals(emptyList<Skill>(), snapshot.skills)
        assertEquals(emptyList<TaskSkillLink>(), snapshot.taskSkills)
        assertEquals(emptyList<TaskCompletion>(), snapshot.completions)
        assertEquals(emptyList<SkillRating>(), snapshot.ratings)
    }

    /** Bumping the version would make every existing backup file unreadable. */
    @Test
    fun theFormatVersionStaysAtOneSoOlderBuildsCanStillReadNewFiles() {
        val encoded = org.json.JSONObject(
            TodoBackup.encode(DueSnapshot(todos = listOf(Todo("one", "Buy milk", 1, false)))),
        )

        assertEquals(1, encoded.getInt("version"))
        assertEquals("dev.sharno.due.todos", encoded.getString("format"))
        assertEquals(1, encoded.getJSONArray("todos").length())
    }
}
