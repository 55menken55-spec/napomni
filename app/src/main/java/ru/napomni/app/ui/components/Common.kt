package ru.napomni.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.napomni.app.data.model.NoteColor
import ru.napomni.app.domain.formatDate
import ru.napomni.app.domain.formatTime
import ru.napomni.app.domain.weekdayShort
import ru.napomni.app.ui.theme.composeColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Заголовок секции в формах и списках. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

/** Подпись над полем. */
@Composable
fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/** Выбор одного из 8 цветов метки. */
@Composable
fun ColorDotPicker(
    selected: NoteColor,
    onSelect: (NoteColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NoteColor.entries.forEach { color ->
            val isSelected = color == selected
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.composeColor())
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    )
                    .clickable { onSelect(color) },
            )
        }
    }
}

/** Выбор дней недели (для еженедельных напоминаний). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekdayPicker(
    selected: List<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DayOfWeek.values().forEach { day ->
            FilterChip(
                selected = selected.contains(day),
                onClick = { onToggle(day) },
                label = { Text(weekdayShort(day)) },
            )
        }
    }
}

/** Список времён суток с добавлением/удалением (1–6 штук). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimesEditor(
    times: List<LocalTime>,
    onChange: (List<LocalTime>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            times.sorted().forEach { time ->
                InputChip(
                    selected = false,
                    onClick = {},
                    label = { Text(formatTime(time)) },
                    trailingIcon = {
                        IconButton(
                            onClick = { onChange(times.filterNot { it == time }) },
                            modifier = Modifier.size(18.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Убрать время")
                        }
                    },
                )
            }
        }
        TextButton(onClick = { showPicker = true }, enabled = times.size < 6) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Добавить время")
        }
    }
    if (showPicker) {
        AppTimePickerDialog(
            initial = LocalTime.of(9, 0),
            onDismiss = { showPicker = false },
            onConfirm = { time ->
                onChange((times + time).distinct().sorted())
                showPicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp)) {
                Text("Выберите время", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                TimePicker(state = state)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    TextButton(
                        onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) },
                    ) { Text("ОК") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val picked = state.selectedDateMillis?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                }
                if (picked != null) onConfirm(picked) else onDismiss()
            }) { Text("ОК") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    ) {
        DatePicker(state = state)
    }
}

/** Строка «Дата/время → кнопка Выбрать». */
@Composable
fun DateFieldRow(
    label: String,
    date: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Column {
        FieldLabel(label)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(formatDate(date), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.width(16.dp))
            TextButton(onClick = { showPicker = true }) { Text("Выбрать") }
        }
    }
    if (showPicker) {
        AppDatePickerDialog(
            initial = date,
            onDismiss = { showPicker = false },
            onConfirm = {
                onPick(it)
                showPicker = false
            },
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "Удалить",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/** Удобная обёртка: «сегодня в HH:mm» для будущих срабатываний. */
fun describeMoment(moment: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): String {
    val today = now.toLocalDate()
    return when (moment.toLocalDate()) {
        today -> "сегодня в ${formatTime(moment.toLocalTime())}"
        today.plusDays(1) -> "завтра в ${formatTime(moment.toLocalTime())}"
        else -> "${formatDate(moment.toLocalDate())} в ${formatTime(moment.toLocalTime())}"
    }
}
