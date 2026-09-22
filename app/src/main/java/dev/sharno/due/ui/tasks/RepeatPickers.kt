package dev.sharno.due.ui.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sharno.due.RecurrenceEnd
import dev.sharno.due.RecurrenceFrequency
import dev.sharno.due.RecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate

internal enum class RepeatPattern(val label: String) {
    NONE("Does not repeat"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Annually"),
    WEEKDAYS("Every weekday"),
    CUSTOM("Custom"),
}

internal enum class RepeatUnit(val label: String) {
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year"),
}

internal enum class RepeatEndMode(val label: String) {
    NEVER("Never"),
    ON_DATE("On date"),
    AFTER_OCCURRENCES("After occurrences"),
}

@Composable
internal fun RepeatPatternPicker(
    selected: RepeatPattern,
    onSelected: (RepeatPattern) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RepeatPattern.values().forEach { pattern ->
                DropdownMenuItem(
                    text = { Text(pattern.label) },
                    onClick = {
                        expanded = false
                        onSelected(pattern)
                    },
                )
            }
        }
    }
}

@Composable
internal fun RepeatUnitPicker(
    selected: RepeatUnit,
    onSelected: (RepeatUnit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { expanded = true }) { Text(selected.label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RepeatUnit.values().forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit.label) },
                    onClick = {
                        expanded = false
                        onSelected(unit)
                    },
                )
            }
        }
    }
}

@Composable
internal fun RepeatEndPicker(
    selected: RepeatEndMode,
    onSelected: (RepeatEndMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { expanded = true }) { Text(selected.label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RepeatEndMode.values().forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        expanded = false
                        onSelected(mode)
                    },
                )
            }
        }
    }
}

@Composable
internal fun WeekdayChips(
    selected: Set<DayOfWeek>,
    onSelected: (Set<DayOfWeek>) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DayOfWeek.values().forEach { day ->
            FilterChip(
                selected = day in selected,
                onClick = {
                    onSelected(
                        if (day in selected) {
                            if (selected.size == 1) selected else selected - day
                        } else {
                            selected + day
                        },
                    )
                },
                label = { Text(day.shortLabel()) },
            )
        }
    }
}

internal fun recurrenceFromSelection(
    pattern: RepeatPattern,
    dueAtMillis: Long,
    selectedWeekdays: Set<DayOfWeek>,
    customUnit: RepeatUnit,
    customInterval: String,
    customMonthDay: String,
    endMode: RepeatEndMode,
    endDateMillis: Long,
    occurrences: String,
): RecurrenceRule? {
    if (pattern == RepeatPattern.NONE) return null

    val dueDate = dueAtMillis.asLocalDateTime().toLocalDate()
    val end = when (endMode) {
        RepeatEndMode.NEVER -> RecurrenceEnd.Never
        RepeatEndMode.ON_DATE -> RecurrenceEnd.On(
            endDateMillis.asLocalDateTime().toLocalDate().also {
                require(!it.isBefore(dueDate)) { "The end date must be on or after the due date" }
            },
        )

        RepeatEndMode.AFTER_OCCURRENCES -> RecurrenceEnd.After(
            occurrences.toIntOrNull()?.also {
                require(it > 0) { "Occurrences must be greater than zero" }
            } ?: error("Enter a valid number of occurrences"),
        )
    }

    return when (pattern) {
        RepeatPattern.DAILY -> RecurrenceRule(RecurrenceFrequency.DAILY, end = end)
        RepeatPattern.WEEKLY -> RecurrenceRule(
            frequency = RecurrenceFrequency.WEEKLY,
            daysOfWeek = selectedWeekdays,
            end = end,
        )

        RepeatPattern.MONTHLY -> RecurrenceRule(
            frequency = RecurrenceFrequency.MONTHLY,
            dayOfMonth = dueDate.dayOfMonth,
            end = end,
        )

        RepeatPattern.YEARLY -> RecurrenceRule(
            frequency = RecurrenceFrequency.YEARLY,
            dayOfMonth = dueDate.dayOfMonth,
            monthOfYear = dueDate.monthValue,
            end = end,
        )

        RepeatPattern.WEEKDAYS -> RecurrenceRule(RecurrenceFrequency.WEEKDAYS, end = end)
        RepeatPattern.CUSTOM -> {
            val interval = customInterval.toIntOrNull()?.also {
                require(it > 0) { "The repeat interval must be greater than zero" }
            } ?: error("Enter a valid repeat interval")
            when (customUnit) {
                RepeatUnit.DAY -> RecurrenceRule(RecurrenceFrequency.DAILY, interval, end = end)
                RepeatUnit.WEEK -> RecurrenceRule(
                    frequency = RecurrenceFrequency.WEEKLY,
                    interval = interval,
                    daysOfWeek = selectedWeekdays,
                    end = end,
                )

                RepeatUnit.MONTH -> RecurrenceRule(
                    frequency = RecurrenceFrequency.MONTHLY,
                    interval = interval,
                    dayOfMonth = customMonthDay.toIntOrNull()?.also {
                        require(it in 1..31) { "The monthly day must be from 1 to 31" }
                    } ?: error("Enter a valid monthly day"),
                    end = end,
                )

                RepeatUnit.YEAR -> RecurrenceRule(
                    frequency = RecurrenceFrequency.YEARLY,
                    interval = interval,
                    dayOfMonth = dueDate.dayOfMonth,
                    monthOfYear = dueDate.monthValue,
                    end = end,
                )
            }
        }

        RepeatPattern.NONE -> null
    }
}

/** The repeat-picker state that produces a given [RecurrenceRule]. */
internal data class RepeatSelection(
    val pattern: RepeatPattern,
    val customUnit: RepeatUnit = RepeatUnit.WEEK,
    val customInterval: String = "1",
    val selectedWeekdays: Set<DayOfWeek>,
    val customMonthDay: String,
    val endMode: RepeatEndMode = RepeatEndMode.NEVER,
    val endDate: LocalDate? = null,
    val occurrences: String = "10",
)

/**
 * Inverts [recurrenceFromSelection] so an existing task can be reopened for editing.
 *
 * The subtle part is that the simple patterns only represent an interval of one: a weekly rule that
 * repeats every three weeks has to come back as CUSTOM, not WEEKLY, or saving it again would quietly
 * change the task to repeat weekly. `RecurrenceSelectionTest` pins the round trip.
 */
internal fun RecurrenceRule?.toSelection(dueDate: LocalDate): RepeatSelection {
    val defaults = RepeatSelection(
        pattern = RepeatPattern.NONE,
        selectedWeekdays = setOf(dueDate.dayOfWeek),
        customMonthDay = dueDate.dayOfMonth.toString(),
    )
    val rule = this ?: return defaults

    val endMode = when (rule.end) {
        is RecurrenceEnd.Never -> RepeatEndMode.NEVER
        is RecurrenceEnd.On -> RepeatEndMode.ON_DATE
        is RecurrenceEnd.After -> RepeatEndMode.AFTER_OCCURRENCES
    }
    val base = defaults.copy(
        endMode = endMode,
        endDate = (rule.end as? RecurrenceEnd.On)?.date,
        occurrences = (rule.end as? RecurrenceEnd.After)?.occurrences?.toString() ?: defaults.occurrences,
        selectedWeekdays = rule.daysOfWeek.ifEmpty { setOf(dueDate.dayOfWeek) },
        customMonthDay = (rule.dayOfMonth ?: dueDate.dayOfMonth).toString(),
    )

    val simple = rule.interval == 1
    return when (rule.frequency) {
        RecurrenceFrequency.WEEKDAYS -> base.copy(pattern = RepeatPattern.WEEKDAYS)
        RecurrenceFrequency.DAILY -> if (simple) {
            base.copy(pattern = RepeatPattern.DAILY)
        } else {
            base.copy(pattern = RepeatPattern.CUSTOM, customUnit = RepeatUnit.DAY, customInterval = rule.interval.toString())
        }

        RecurrenceFrequency.WEEKLY -> if (simple) {
            base.copy(pattern = RepeatPattern.WEEKLY)
        } else {
            base.copy(pattern = RepeatPattern.CUSTOM, customUnit = RepeatUnit.WEEK, customInterval = rule.interval.toString())
        }

        RecurrenceFrequency.MONTHLY -> if (simple && rule.dayOfMonth == dueDate.dayOfMonth) {
            base.copy(pattern = RepeatPattern.MONTHLY)
        } else {
            base.copy(pattern = RepeatPattern.CUSTOM, customUnit = RepeatUnit.MONTH, customInterval = rule.interval.toString())
        }

        RecurrenceFrequency.YEARLY -> if (
            simple &&
            rule.dayOfMonth == dueDate.dayOfMonth &&
            rule.monthOfYear == dueDate.monthValue
        ) {
            base.copy(pattern = RepeatPattern.YEARLY)
        } else {
            base.copy(pattern = RepeatPattern.CUSTOM, customUnit = RepeatUnit.YEAR, customInterval = rule.interval.toString())
        }
    }
}
