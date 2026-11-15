package com.neighborly.app.feature.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.neighborly.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val display = DateTimeFormatter.ofPattern("EEE, d MMM yyyy, h:mm a", Locale.getDefault())
private val displayDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

/** Picks a local date + time; shows the value read-only and opens date then time dialogs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(label: String, value: LocalDateTime?, onChange: (LocalDateTime?) -> Unit, clearable: Boolean = false) {
    var step by remember { mutableStateOf(0) } // 0 closed, 1 date, 2 time
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    FieldBox(label, value?.format(display).orEmpty(), clearable && value != null, onOpen = { step = 1 }, onClear = { onChange(null) })

    if (step == 1) {
        val initial = (value ?: LocalDateTime.now()).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { step = 0 },
            confirmButton = {
                TextButton(onClick = {
                    pickedDate = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    step = if (pickedDate != null) 2 else 0
                }) { Text(stringResource(R.string.action_next)) }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
    }
    if (step == 2) {
        val t = value?.toLocalTime() ?: LocalTime.of(18, 0)
        val state = rememberTimePickerState(initialHour = t.hour, initialMinute = t.minute)
        AlertDialog(
            onDismissRequest = { step = 0 },
            confirmButton = {
                TextButton(onClick = {
                    pickedDate?.let { onChange(it.atTime(state.hour, state.minute)) }
                    step = 0
                }) { Text(stringResource(R.string.action_done)) }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text(stringResource(R.string.action_cancel)) } },
            text = { TimePicker(state) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: LocalDate?, onChange: (LocalDate?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    FieldBox(label, value?.format(displayDate).orEmpty(), value != null, onOpen = { open = true }, onClear = { onChange(null) })
    if (open) {
        val initial = (value ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text(stringResource(R.string.action_done)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun FieldBox(label: String, text: String, clearable: Boolean, onOpen: () -> Unit, onClear: () -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = {},
        readOnly = true,
        enabled = false,
        label = { Text(label) },
        trailingIcon = {
            if (clearable) IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, stringResource(R.string.action_clear)) }
            else Icon(Icons.Outlined.CalendarMonth, null)
        },
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            disabledTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            disabledLabelColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            disabledBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outline,
            disabledTrailingIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    )
}

/** Local date-time ↔ ISO instant string (timestamptz). */
fun LocalDateTime.toIso(): String = atZone(ZoneId.systemDefault()).toInstant().toString()
fun String.toLocalDateTime(): LocalDateTime? =
    com.neighborly.app.core.util.Format.parseInstant(this)?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
