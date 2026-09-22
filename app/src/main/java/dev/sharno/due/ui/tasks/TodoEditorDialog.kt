package dev.sharno.due.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sharno.due.RecurrenceRule
import dev.sharno.due.Skill
import dev.sharno.due.Todo
import dev.sharno.due.ui.skills.SkillPicker
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@Composable
internal fun TodoEditorDialog(
    initial: Todo?,
    allSkills: List<Skill>,
    initialSkillIds: Set<String>,
    onDismiss: () -> Unit,
    onCreateSkill: () -> Unit,
    onSave: (title: String, dueAtMillis: Long, recurrence: RecurrenceRule?, skillIds: Set<String>) -> Unit,
) {
    val startMillis = initial?.dueAtMillis ?: System.currentTimeMillis()
    // Rebuild the repeat pickers from the existing rule; see RepeatSelection.toSelection.
    val selection = remember(initial) {
        initial?.recurrence.toSelection(startMillis.asLocalDateTime().toLocalDate())
    }

    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var dueAtMillis by remember { mutableLongStateOf(startMillis) }
    var showTitleError by remember { mutableStateOf(false) }
    var recurrenceError by remember { mutableStateOf<String?>(null) }
    var repeatPattern by remember { mutableStateOf(selection.pattern) }
    var customUnit by remember { mutableStateOf(selection.customUnit) }
    var customInterval by remember { mutableStateOf(selection.customInterval) }
    var selectedWeekdays by remember { mutableStateOf(selection.selectedWeekdays) }
    var customMonthDay by remember { mutableStateOf(selection.customMonthDay) }
    var repeatEndMode by remember { mutableStateOf(selection.endMode) }
    var repeatEndDateMillis by remember {
        mutableLongStateOf(
            selection.endDate
                ?.atStartOfDay(ZoneId.systemDefault())
                ?.toInstant()
                ?.toEpochMilli()
                ?: startMillis,
        )
    }
    var repeatOccurrences by remember { mutableStateOf(selection.occurrences) }
    var selectedSkillIds by remember(initialSkillIds) { mutableStateOf(initialSkillIds) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New todo" else "Edit todo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        showTitleError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("What needs doing?") },
                    singleLine = true,
                    isError = showTitleError,
                )
                Text("Due date and time", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showDatePicker = true }) { Text(formatDate(dueAtMillis)) }
                    Button(onClick = { showTimePicker = true }) { Text(formatTime(dueAtMillis)) }
                }
                Text("Repeat", style = MaterialTheme.typography.labelLarge)
                RepeatPatternPicker(
                    selected = repeatPattern,
                    onSelected = {
                        repeatPattern = it
                        recurrenceError = null
                    },
                )
                if (repeatPattern != RepeatPattern.NONE) {
                    when (repeatPattern) {
                        RepeatPattern.WEEKLY -> {
                            Text("Repeat on", style = MaterialTheme.typography.labelLarge)
                            WeekdayChips(
                                selected = selectedWeekdays,
                                onSelected = { selectedWeekdays = it },
                            )
                        }

                        RepeatPattern.MONTHLY -> {
                            Text("On day ${dueAtMillis.asLocalDateTime().dayOfMonth}")
                        }

                        RepeatPattern.YEARLY -> {
                            Text("On ${formatDate(dueAtMillis)}")
                        }

                        RepeatPattern.CUSTOM -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("Every")
                                OutlinedTextField(
                                    value = customInterval,
                                    onValueChange = {
                                        if (it.all(Char::isDigit)) customInterval = it
                                    },
                                    modifier = Modifier.width(84.dp),
                                    singleLine = true,
                                )
                                RepeatUnitPicker(
                                    selected = customUnit,
                                    onSelected = { customUnit = it },
                                )
                            }
                            if (customUnit == RepeatUnit.WEEK) {
                                Text("Repeat on", style = MaterialTheme.typography.labelLarge)
                                WeekdayChips(
                                    selected = selectedWeekdays,
                                    onSelected = { selectedWeekdays = it },
                                )
                            }
                            if (customUnit == RepeatUnit.MONTH) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text("On day")
                                    OutlinedTextField(
                                        value = customMonthDay,
                                        onValueChange = {
                                            if (it.all(Char::isDigit)) customMonthDay = it
                                        },
                                        modifier = Modifier.width(84.dp),
                                        singleLine = true,
                                    )
                                }
                            }
                        }

                        RepeatPattern.DAILY,
                        RepeatPattern.WEEKDAYS,
                        RepeatPattern.NONE,
                        -> Unit
                    }

                    Text("Ends", style = MaterialTheme.typography.labelLarge)
                    RepeatEndPicker(
                        selected = repeatEndMode,
                        onSelected = { repeatEndMode = it },
                    )
                    when (repeatEndMode) {
                        RepeatEndMode.NEVER -> Unit
                        RepeatEndMode.ON_DATE -> {
                            Button(onClick = { showEndDatePicker = true }) {
                                Text(formatDate(repeatEndDateMillis))
                            }
                        }

                        RepeatEndMode.AFTER_OCCURRENCES -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = repeatOccurrences,
                                    onValueChange = {
                                        if (it.all(Char::isDigit)) repeatOccurrences = it
                                    },
                                    modifier = Modifier.width(100.dp),
                                    singleLine = true,
                                )
                                Text("occurrences")
                            }
                        }
                    }
                }
                Text("Skills", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Rate these when you tick this task off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SkillPicker(
                    skills = allSkills,
                    selectedIds = selectedSkillIds,
                    onToggle = { skill ->
                        selectedSkillIds = if (skill.id in selectedSkillIds) {
                            selectedSkillIds - skill.id
                        } else {
                            selectedSkillIds + skill.id
                        }
                    },
                    onCreateSkill = onCreateSkill,
                )
                recurrenceError?.let { error ->
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isBlank()) {
                        showTitleError = true
                    } else {
                        runCatching {
                            recurrenceFromSelection(
                                pattern = repeatPattern,
                                dueAtMillis = dueAtMillis,
                                selectedWeekdays = selectedWeekdays,
                                customUnit = customUnit,
                                customInterval = customInterval,
                                customMonthDay = customMonthDay,
                                endMode = repeatEndMode,
                                endDateMillis = repeatEndDateMillis,
                                occurrences = repeatOccurrences,
                            )
                        }.fold(
                            onSuccess = { recurrence ->
                                onSave(title, dueAtMillis, recurrence, selectedSkillIds)
                            },
                            onFailure = { error -> recurrenceError = error.message ?: "Invalid repeat settings" },
                        )
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showDatePicker) {
        DueDatePickerDialog(
            initialMillis = dueAtMillis,
            onDismiss = { showDatePicker = false },
            onDateSelected = { nextDate ->
                val previousDueAtMillis = dueAtMillis
                val previousDate = previousDueAtMillis.asLocalDateTime().toLocalDate()
                dueAtMillis = LocalDateTime.of(
                    nextDate,
                    previousDueAtMillis.asLocalDateTime().toLocalTime(),
                ).toMillis()
                if (selectedWeekdays == setOf(previousDate.dayOfWeek)) {
                    selectedWeekdays = setOf(nextDate.dayOfWeek)
                }
                if (customMonthDay == previousDate.dayOfMonth.toString()) {
                    customMonthDay = nextDate.dayOfMonth.toString()
                }
                if (repeatEndDateMillis == previousDueAtMillis) {
                    repeatEndDateMillis = dueAtMillis
                }
            },
        )
    }

    if (showTimePicker) {
        DueTimePickerDialog(
            initialMillis = dueAtMillis,
            onDismiss = { showTimePicker = false },
            onTimeSelected = { hour, minute ->
                dueAtMillis = LocalDateTime.of(
                    dueAtMillis.asLocalDateTime().toLocalDate(),
                    LocalTime.of(hour, minute),
                ).toMillis()
            },
        )
    }

    if (showEndDatePicker) {
        DueDatePickerDialog(
            initialMillis = repeatEndDateMillis,
            notBeforeMillis = dueAtMillis,
            onDismiss = { showEndDatePicker = false },
            onDateSelected = { date ->
                repeatEndDateMillis = date.atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            },
        )
    }
}
