package ru.napomni.app.ui.reminders

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.napomni.app.data.model.EndMode
import ru.napomni.app.data.model.ReminderImportance
import ru.napomni.app.data.model.ScheduleType
import ru.napomni.app.domain.formatTime
import ru.napomni.app.domain.PermissionChecks
import ru.napomni.app.domain.PermissionKind
import ru.napomni.app.ui.AppViewModelProvider
import ru.napomni.app.ui.categories.CategoriesViewModel
import ru.napomni.app.ui.components.AppTimePickerDialog
import ru.napomni.app.ui.components.CategoryEditDialog
import ru.napomni.app.ui.components.CategoryPickerRow
import ru.napomni.app.ui.components.ColorDotPicker
import ru.napomni.app.ui.components.DateFieldRow
import ru.napomni.app.ui.components.FieldLabel
import ru.napomni.app.ui.components.SectionHeader
import ru.napomni.app.ui.components.TimesEditor
import ru.napomni.app.ui.components.WeekdayPicker
import java.time.DayOfWeek
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReminderEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
    categoriesViewModel: CategoriesViewModel = viewModel(
        factory = AppViewModelProvider.Factory,
        key = "categories",
    ),
) {
    val state by viewModel.state.collectAsState()
    val categories by categoriesViewModel.categories.collectAsState()
    val context = LocalContext.current
    var showDelete by remember { mutableStateOf(false) }
    var showCategoryCreate by remember { mutableStateOf(false) }

    // Напоминание можно сохранить и без разрешения на уведомления, но показывать его
    // телефон не будет — предупреждаем сразу, а не «тишиной» в назначенное время.
    var showNotificationsOff by remember { mutableStateOf(false) }
    var afterSave by remember { mutableStateOf<(() -> Unit)?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        showNotificationsOff = false
        if (!granted) {
            PermissionChecks.openSettings(context, PermissionKind.NOTIFICATIONS)
        }
        afterSave?.invoke()
        afterSave = null
    }

    fun saveReminder() {
        viewModel.save {
            if (PermissionChecks.notificationsAllowed(context)) {
                onDone()
            } else {
                afterSave = onDone
                showNotificationsOff = true
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (state.id == 0L) "Новое напоминание" else "Изменить") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(onClick = { saveReminder() }) {
                        Text("Сохранить")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = { text -> viewModel.update { it.copy(title = text) } },
                label = { Text("Заголовок *") },
                placeholder = { Text("Например: Выпить таблетки") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.details,
                onValueChange = { text -> viewModel.update { it.copy(details = text) } },
                label = { Text("Детали (необязательно)") },
                placeholder = { Text("Например: после приёма пищи") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader("Расписание")
            ScheduleType.values().forEach { type ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.scheduleType == type,
                        onClick = { viewModel.update { it.copy(scheduleType = type) } },
                    )
                    Text(
                        when (type) {
                            ScheduleType.ONCE -> "Разовое"
                            ScheduleType.DAILY -> "Каждый день"
                            ScheduleType.WEEKLY -> "По дням недели"
                            ScheduleType.INTERVAL_DAYS -> "Каждые N дней"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            when (state.scheduleType) {
                ScheduleType.ONCE -> {
                    DateFieldRow(
                        label = "Дата",
                        date = state.startDate,
                        onPick = { date -> viewModel.update { it.copy(startDate = date) } },
                    )
                    TimeFieldRow(
                        label = "Время",
                        time = state.onceTime,
                        onPick = { time -> viewModel.update { it.copy(onceTime = time) } },
                    )
                }
                ScheduleType.DAILY -> {
                    FieldLabel("Времена срабатывания")
                    TimesEditor(
                        times = state.times,
                        onChange = { times -> viewModel.update { it.copy(times = times) } },
                    )
                }
                ScheduleType.WEEKLY -> {
                    FieldLabel("Дни недели")
                    WeekdayPicker(
                        selected = state.weekdays,
                        onToggle = { day ->
                            viewModel.update { form ->
                                val days = if (form.weekdays.contains(day)) {
                                    form.weekdays - day
                                } else {
                                    form.weekdays + day
                                }
                                form.copy(weekdays = days)
                            }
                        },
                    )
                    FieldLabel("Времена срабатывания")
                    TimesEditor(
                        times = state.times,
                        onChange = { times -> viewModel.update { it.copy(times = times) } },
                    )
                }
                ScheduleType.INTERVAL_DAYS -> {
                    FieldLabel("Интервал (дней)")
                    OutlinedTextField(
                        value = state.intervalDays,
                        onValueChange = { text ->
                            viewModel.update {
                                it.copy(intervalDays = text.filter(Char::isDigit).take(3))
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(0.5f),
                    )
                    DateFieldRow(
                        label = "Начиная с",
                        date = state.startDate,
                        onPick = { date -> viewModel.update { it.copy(startDate = date) } },
                    )
                    FieldLabel("Времена срабатывания")
                    TimesEditor(
                        times = state.times,
                        onChange = { times -> viewModel.update { it.copy(times = times) } },
                    )
                }
            }

            SectionHeader("Окончание")
            EndMode.values().forEach { mode ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.endMode == mode,
                        onClick = { viewModel.update { it.copy(endMode = mode) } },
                    )
                    Text(
                        when (mode) {
                            EndMode.NEVER -> "Без окончания"
                            EndMode.UNTIL_DATE -> "До даты"
                            EndMode.AFTER_COUNT -> "После N срабатываний (курс)"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            when (state.endMode) {
                EndMode.UNTIL_DATE -> DateFieldRow(
                    label = "Дата окончания",
                    date = state.endDate,
                    onPick = { date -> viewModel.update { it.copy(endDate = date) } },
                )
                EndMode.AFTER_COUNT -> {
                    FieldLabel("Сколько срабатываний")
                    OutlinedTextField(
                        value = state.maxOccurrences,
                        onValueChange = { text ->
                            viewModel.update {
                                it.copy(maxOccurrences = text.filter(Char::isDigit).take(4))
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(0.5f),
                    )
                }
                EndMode.NEVER -> Unit
            }

            SectionHeader("Важность срабатывания")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.importance == ReminderImportance.NOTIFICATION,
                    onClick = {
                        viewModel.update { it.copy(importance = ReminderImportance.NOTIFICATION) }
                    },
                    label = { Text("Уведомление") },
                )
                FilterChip(
                    selected = state.importance == ReminderImportance.ALARM,
                    onClick = {
                        viewModel.update { it.copy(importance = ReminderImportance.ALARM) }
                    },
                    label = { Text("Как будильник") },
                )
            }
            Text(
                if (state.importance == ReminderImportance.ALARM) {
                    "Громкий сигнал поверх экрана блокировки, пока не ответите (для лекарств)."
                } else {
                    "Обычное уведомление со звуком и кнопками действий."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeader("Цвет метки")
            ColorDotPicker(
                selected = state.color,
                onSelect = { color -> viewModel.update { it.copy(color = color) } },
            )

            SectionHeader("Категория")
            CategoryPickerRow(
                categories = categories,
                selectedId = state.categoryId,
                onSelect = { categoryId -> viewModel.update { it.copy(categoryId = categoryId) } },
                onAddClick = { showCategoryCreate = true },
            )

            SectionHeader("Дополнительно")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Активно", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = state.enabled,
                    onCheckedChange = { on -> viewModel.update { it.copy(enabled = on) } },
                )
            }

            state.error?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { saveReminder() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить")
            }
            if (state.id > 0) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { showDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Удалить напоминание", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showNotificationsOff) {
        AlertDialog(
            onDismissRequest = {
                showNotificationsOff = false
                afterSave?.invoke()
                afterSave = null
            },
            title = { Text("Уведомления запрещены") },
            text = {
                Text(
                    "Напоминание сохранено, но пока показ уведомлений запрещён, телефон " +
                        "о нём не сообщит. Разрешить уведомления сейчас?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationsOff = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        PermissionChecks.openSettings(context, PermissionKind.NOTIFICATIONS)
                        afterSave?.invoke()
                        afterSave = null
                    }
                }) { Text("Разрешить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNotificationsOff = false
                    afterSave?.invoke()
                    afterSave = null
                }) { Text("Позже") }
            },
        )
    }

    if (showCategoryCreate) {
        CategoryEditDialog(
            category = null,
            onSave = { name, color -> categoriesViewModel.add(name, color) },
            onDismiss = { showCategoryCreate = false },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Удалить напоминание?") },
            text = { Text("«${state.title}» будет удалено без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.delete(onDone)
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Отмена") }
            },
        )
    }
}

/** Строка «Время → кнопка Выбрать» (для разовых напоминаний). */
@Composable
private fun TimeFieldRow(
    label: String,
    time: LocalTime,
    onPick: (LocalTime) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Column {
        FieldLabel(label)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(formatTime(time), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.width(16.dp))
            TextButton(onClick = { showPicker = true }) { Text("Выбрать") }
        }
    }
    if (showPicker) {
        AppTimePickerDialog(
            initial = time,
            onDismiss = { showPicker = false },
            onConfirm = {
                onPick(it)
                showPicker = false
            },
        )
    }
}
