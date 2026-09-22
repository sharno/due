package dev.sharno.due.ui.tasks

import dev.sharno.due.RecurrenceEnd
import dev.sharno.due.RecurrenceFrequency
import dev.sharno.due.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * Editing an existing task has to rebuild the repeat pickers from its rule. Getting that wrong is
 * silent and destructive — a rule that repeats every three weeks coming back as plain "Weekly"
 * would change the task the next time it is saved.
 */
class RecurrenceSelectionTest {
    private val dueDate = LocalDate.of(2026, 3, 11) // a Wednesday
    private val endDate = LocalDate.of(2027, 1, 1)

    private val rules = listOf(
        null,
        RecurrenceRule(RecurrenceFrequency.DAILY),
        RecurrenceRule(RecurrenceFrequency.DAILY, interval = 3),
        RecurrenceRule(RecurrenceFrequency.WEEKDAYS),
        RecurrenceRule(RecurrenceFrequency.WEEKLY, daysOfWeek = setOf(DayOfWeek.WEDNESDAY)),
        RecurrenceRule(
            frequency = RecurrenceFrequency.WEEKLY,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
        ),
        RecurrenceRule(
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 3,
            daysOfWeek = setOf(DayOfWeek.WEDNESDAY),
        ),
        RecurrenceRule(RecurrenceFrequency.MONTHLY, dayOfMonth = 11),
        RecurrenceRule(RecurrenceFrequency.MONTHLY, interval = 2, dayOfMonth = 11),
        RecurrenceRule(RecurrenceFrequency.YEARLY, dayOfMonth = 11, monthOfYear = 3),
        RecurrenceRule(RecurrenceFrequency.YEARLY, interval = 2, dayOfMonth = 11, monthOfYear = 3),
        RecurrenceRule(
            frequency = RecurrenceFrequency.DAILY,
            end = RecurrenceEnd.On(endDate),
        ),
        RecurrenceRule(
            frequency = RecurrenceFrequency.WEEKLY,
            daysOfWeek = setOf(DayOfWeek.WEDNESDAY),
            end = RecurrenceEnd.After(12),
        ),
        RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 4,
            dayOfMonth = 11,
            end = RecurrenceEnd.After(5),
        ),
    )

    @Test
    fun everyRuleTheEditorCanProduceSurvivesAnEditRoundTrip() {
        for (rule in rules) {
            assertEquals("round trip changed the rule", rule, rule.roundTrip())
        }
    }

    @Test
    fun anIntervalGreaterThanOneBecomesACustomPattern() {
        val everyThreeWeeks = RecurrenceRule(
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 3,
            daysOfWeek = setOf(DayOfWeek.WEDNESDAY),
        )
        val selection = everyThreeWeeks.toSelection(dueDate)
        assertEquals(RepeatPattern.CUSTOM, selection.pattern)
        assertEquals(RepeatUnit.WEEK, selection.customUnit)
        assertEquals("3", selection.customInterval)
    }

    @Test
    fun aPlainIntervalOfOneKeepsItsSimplePattern() {
        val weekly = RecurrenceRule(
            frequency = RecurrenceFrequency.WEEKLY,
            daysOfWeek = setOf(DayOfWeek.WEDNESDAY),
        )
        assertEquals(RepeatPattern.WEEKLY, weekly.toSelection(dueDate).pattern)
    }

    @Test
    fun noRecurrenceStillSeedsTheWeekdayAndMonthDayFromTheDueDate() {
        val selection = null.toSelection(dueDate)
        assertEquals(RepeatPattern.NONE, selection.pattern)
        assertEquals(setOf(DayOfWeek.WEDNESDAY), selection.selectedWeekdays)
        assertEquals("11", selection.customMonthDay)
    }

    private fun RecurrenceRule?.roundTrip(): RecurrenceRule? {
        val selection = toSelection(dueDate)
        return recurrenceFromSelection(
            pattern = selection.pattern,
            dueAtMillis = dueDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            selectedWeekdays = selection.selectedWeekdays,
            customUnit = selection.customUnit,
            customInterval = selection.customInterval,
            customMonthDay = selection.customMonthDay,
            endMode = selection.endMode,
            endDateMillis = (selection.endDate ?: dueDate)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),
            occurrences = selection.occurrences,
        )
    }
}
