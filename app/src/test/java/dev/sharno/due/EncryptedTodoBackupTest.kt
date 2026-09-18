package dev.sharno.due

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EncryptedTodoBackupTest {
    private val todos = listOf(
        Todo("one", "Buy milk", 1_700_000_000_000, false),
        Todo("two", "Read book", 1_700_000_001_000, true),
    )

    @Test
    fun roundTripPreservesTodosWithoutExposingPlaintext() {
        val backup = EncryptedTodoBackup.encode(todos, "correct horse battery staple")

        assertEquals(
            todos,
            EncryptedTodoBackup.decode(backup, "correct horse battery staple"),
        )
        assertFalse(backup.contains("Buy milk"))
        assertFalse(backup.contains("Read book"))
    }

    @Test
    fun encryptionsUseDifferentRandomParameters() {
        val first = EncryptedTodoBackup.encode(todos, "same passphrase")
        val second = EncryptedTodoBackup.encode(todos, "same passphrase")

        assertNotEquals(first, second)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongPassphrase() {
        val backup = EncryptedTodoBackup.encode(todos, "correct passphrase")

        EncryptedTodoBackup.decode(backup, "wrong passphrase")
    }
}
