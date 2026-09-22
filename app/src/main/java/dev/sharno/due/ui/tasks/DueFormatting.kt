package dev.sharno.due.ui.tasks

import dev.sharno.due.RecurrenceFrequency
import dev.sharno.due.RecurrenceRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal fun recurrenceSummary(rule: RecurrenceRule): String = when (rule.frequency) {
    RecurrenceFrequency.DAILY -> "Repeats every ${rule.interval} day${if (rule.interval == 1) "" else "s"}"
    RecurrenceFrequency.WEEKDAYS -> "Repeats every weekday"
    RecurrenceFrequency.WEEKLY -> "Repeats every ${rule.interval} week${if (rule.interval == 1) "" else "s"} on " +
        rule.daysOfWeek.sortedBy(DayOfWeek::getValue).joinToString(", ") { it.shortLabel() }
    RecurrenceFrequency.MONTHLY -> "Repeats every ${rule.interval} month${if (rule.interval == 1) "" else "s"} on day ${rule.dayOfMonth}"
    RecurrenceFrequency.YEARLY -> "Repeats every ${rule.interval} year${if (rule.interval == 1) "" else "s"}"
}

internal fun DayOfWeek.shortLabel(): String = getDisplayName(TextStyle.SHORT, Locale.getDefault())

private val dueDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
private val dueTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dueDateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d · HH:mm")
private val dueDateTimeLongFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy 'at' HH:mm")

internal fun Long.asLocalDateTime(): LocalDateTime = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .toLocalDateTime()

internal fun LocalDateTime.toMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

internal fun formatDate(millis: Long): String = millis.asLocalDateTime().toLocalDate().format(dueDateFormatter)

internal fun formatTime(millis: Long): String = millis.asLocalDateTime().toLocalTime().format(dueTimeFormatter)

internal fun formatDueAt(millis: Long): String = millis.asLocalDateTime().format(dueDateTimeFormatter)

/** The unabbreviated form, used where there is room for it such as the expanded row panel. */
internal fun formatDueAtLong(millis: Long): String =
    millis.asLocalDateTime().format(dueDateTimeLongFormatter)
