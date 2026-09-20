package dev.sharno.due

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrenceTest {
    @Test
    fun weeklyRuleMovesAcrossSelectedDaysAndWeeks() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.WEEKLY,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        )

        assertEquals(
            LocalDate.of(2026, 9, 23),
            RecurrenceCalculator.nextDate(LocalDate.of(2026, 9, 21), rule),
        )
        assertEquals(
            LocalDate.of(2026, 9, 28),
            RecurrenceCalculator.nextDate(LocalDate.of(2026, 9, 23), rule),
        )
    }

    @Test
    fun monthlyAndYearlyDatesClampShortMonths() {
        val monthly = RecurrenceRule(RecurrenceFrequency.MONTHLY, dayOfMonth = 31)
        val yearly = RecurrenceRule(
            frequency = RecurrenceFrequency.YEARLY,
            dayOfMonth = 29,
            monthOfYear = 2,
        )

        assertEquals(
            LocalDate.of(2026, 2, 28),
            RecurrenceCalculator.nextDate(LocalDate.of(2026, 1, 31), monthly),
        )
        assertEquals(
            LocalDate.of(2025, 2, 28),
            RecurrenceCalculator.nextDate(LocalDate.of(2024, 2, 29), yearly),
        )
    }

    @Test
    fun weekdaysSkipTheWeekend() {
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKDAYS)

        assertEquals(
            LocalDate.of(2026, 9, 21),
            RecurrenceCalculator.nextDate(LocalDate.of(2026, 9, 18), rule),
        )
    }

    @Test
    fun completingRepeatingTodoSchedulesNextOccurrence() {
        val dueAtMillis = LocalDate.of(2026, 9, 21)
            .atStartOfDay()
            .toInstant(java.time.ZoneOffset.UTC)
            .toEpochMilli()
        val todo = Todo(
            id = "one",
            title = "Water plants",
            dueAtMillis = dueAtMillis,
            completed = false,
            recurrence = RecurrenceRule(RecurrenceFrequency.DAILY),
        )

        val next = todo.completeAt(dueAtMillis)

        assertFalse(next.completed)
        assertEquals(1, next.occurrencesCompleted)
        assertTrue(next.dueAtMillis > dueAtMillis)
    }

    @Test
    fun afterOneOccurrenceEndsOnCompletion() {
        val todo = Todo(
            id = "one",
            title = "Submit report",
            dueAtMillis = 1_700_000_000_000,
            completed = false,
            recurrence = RecurrenceRule(
                frequency = RecurrenceFrequency.DAILY,
                end = RecurrenceEnd.After(1),
            ),
        )

        val completed = todo.completeAt(1_700_000_000_000)

        assertTrue(completed.completed)
        assertEquals(1, completed.occurrencesCompleted)
    }
}
