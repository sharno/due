package dev.sharno.due.ui.tasks

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Compose date and time pickers.
 *
 * The platform `android.app.DatePickerDialog` these replace is themed by `res/values/themes.xml`,
 * not by `MaterialTheme` — it rendered light-on-light once dark mode arrived and could never follow
 * a custom seed colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DueDatePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    notBeforeMillis: Long? = null,
) {
    val earliestUtcDay = notBeforeMillis?.toUtcDateMillis()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis.toUtcDateMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                earliestUtcDay == null || utcTimeMillis >= earliestUtcDay
        },
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { onDateSelected(it.utcMillisToLocalDate()) }
                    onDismiss()
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DueTimePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
) {
    val current = initialMillis.asLocalDateTime()
    val state = rememberTimePickerState(
        initialHour = current.hour,
        initialMinute = current.minute,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(
                onClick = {
                    onTimeSelected(state.hour, state.minute)
                    onDismiss()
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * `DatePickerState` works in UTC, so a local instant has to be expressed as UTC midnight of the
 * same calendar day — otherwise a user east or west of UTC sees the day before or after selected.
 */
internal fun Long.toUtcDateMillis(): Long =
    asLocalDateTime().toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun Long.utcMillisToLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
