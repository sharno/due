package dev.sharno.due

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EncryptedTodoBackupTest {
    private val snapshot = DueSnapshot(
        todos = listOf(
            Todo("one", "Buy milk", 1_700_000_000_000, false),
            Todo("two", "Read book", 1_700_000_001_000, true),
        ),
        skills = listOf(Skill("guitar", "Guitar", 0xFFE65100.toInt(), 5)),
        taskSkills = listOf(TaskSkillLink("one", "guitar")),
        completions = listOf(TaskCompletion("c1", "one", "Buy milk", 0, 1_700_000_002_000, null)),
        ratings = listOf(SkillRating("c1", "guitar", 7, 1_700_000_003_000)),
    )

    @Test
    fun roundTripPreservesTodosWithoutExposingPlaintext() {
        val backup = EncryptedTodoBackup.encode(snapshot, "correct horse battery staple")

        assertEquals(
            snapshot,
            EncryptedTodoBackup.decode(backup, "correct horse battery staple"),
        )
        assertFalse(backup.contains("Buy milk"))
        assertFalse(backup.contains("Read book"))
        // Skill names are as personal as task titles and must not leak into the envelope either.
        assertFalse(backup.contains("Guitar"))
    }

    @Test
    fun encryptionsUseDifferentRandomParameters() {
        val first = EncryptedTodoBackup.encode(snapshot, "same passphrase")
        val second = EncryptedTodoBackup.encode(snapshot, "same passphrase")

        assertNotEquals(first, second)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongPassphrase() {
        val backup = EncryptedTodoBackup.encode(snapshot, "correct passphrase")

        EncryptedTodoBackup.decode(backup, "wrong passphrase")
    }
}
