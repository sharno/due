package dev.sharno.due

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class TodoStatusTest {
    private val now = 1_700_000_000_000

    private fun todo(
        dueAtMillis: Long,
        completed: Boolean = false,
        recurrence: RecurrenceRule? = null,
    ) = Todo("id", "Practise", dueAtMillis, completed, recurrence)

    @Test
    fun dueInTheFutureIsOutstanding() {
        assertEquals(TodoStatus.TODO, todo(now + 1).statusAt(now))
    }

    @Test
    fun dueExactlyNowIsAlreadyOverdue() {
        assertEquals(TodoStatus.OVERDUE, todo(now).statusAt(now))
    }

    @Test
    fun completedWinsOverAPastDueDate() {
        assertEquals(TodoStatus.DONE, todo(now - 1, completed = true).statusAt(now))
    }

    @Test
    fun theDefaultFilterKeepsOverdueTasksVisible() {
        assertTrue(TaskFilter.TODO.matches(TodoStatus.TODO))
        assertTrue(TaskFilter.TODO.matches(TodoStatus.OVERDUE))
        assertFalse(TaskFilter.TODO.matches(TodoStatus.DONE))
    }

    @Test
    fun theOverdueFilterNarrowsRatherThanSplitting() {
        assertFalse(TaskFilter.OVERDUE.matches(TodoStatus.TODO))
        assertTrue(TaskFilter.OVERDUE.matches(TodoStatus.OVERDUE))
    }

    @Test
    fun everyStatusSurvivesTheAllFilter() {
        TodoStatus.entries.forEach { assertTrue(TaskFilter.ALL.matches(it)) }
    }

    @Test
    fun aCompletedRecurringOccurrenceStaysUnderTheDefaultFilter() {
        val weekly = todo(
            dueAtMillis = now - 1,
            recurrence = RecurrenceRule(
                frequency = RecurrenceFrequency.WEEKLY,
                daysOfWeek = setOf(DayOfWeek.MONDAY),
            ),
        )
        assertEquals(TodoStatus.OVERDUE, weekly.statusAt(now))

        // completeAt rolls the occurrence forward instead of flipping `completed`.
        val next = weekly.completeAt(now)
        assertFalse(next.completed)
        assertEquals(TodoStatus.TODO, next.statusAt(now))
        assertTrue(TaskFilter.TODO.matches(next.statusAt(now)))
    }
}
