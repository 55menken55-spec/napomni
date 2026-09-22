package ru.napomni.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.napomni.app.data.model.OccurrenceStatus
import ru.napomni.app.domain.DayStatus
import ru.napomni.app.domain.ReminderStats
import ru.napomni.app.ui.AppViewModelProvider
import ru.napomni.app.ui.components.SectionHeader
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val Green = Color(0xFF7FB685)
private val Yellow = Color(0xFFF6C445)
private val Red = Color(0xFFE57373)
private val Grey = Color(0xFFBDBDBD)

/** Экран статистики (ТЗ, FR-6): сводка, «X из Y», календарь, история. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsState()
    var dayDialog by remember { mutableStateOf<LocalDate?>(null) }
    var reminderDialog by remember { mutableStateOf<ReminderStats?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!state.hasAnyData) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Статистика пока пуста", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Здесь появятся календарь выполнения, сводки «X из Y»\n" +
                            "и история срабатываний — после первых напоминаний.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@LazyColumn
        }

        item(key = "period") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30, 90).forEach { days ->
                    FilterChip(
                        selected = state.periodDays == days,
                        onClick = { viewModel.setPeriod(days) },
                        label = { Text("$days дней") },
                    )
                }
            }
        }

        item(key = "summary") {
            SummaryCard(state)
        }

        if (state.reminderStats.isNotEmpty()) {
            item { SectionHeader("По напоминаниям") }
            items(state.reminderStats.size, key = { "rs-${state.reminderStats[it].reminderId}" }) { index ->
                val stats = state.reminderStats[index]
                ReminderStatsCard(stats, state.periodDays, onClick = { reminderDialog = stats })
            }
        }

        item { SectionHeader("Календарь выполнения") }
        item(key = "calendar") {
            CalendarCard(
                state = state,
                onPrev = { viewModel.shiftMonth(-1) },
                onNext = { viewModel.shiftMonth(1) },
                onDay = { dayDialog = it },
            )
        }

        item { SectionHeader("История срабатываний") }
        item(key = "history-filters") {
            HistoryFilters(state, viewModel)
        }
        if (state.history.isEmpty()) {
            item(key = "history-empty") {
                Text(
                    "Нет записей по выбранным фильтрам.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.history.size, key = { "h-${state.history[it].occurrence.id}" }) { index ->
            HistoryRowCard(state.history[index])
        }
    }

    dayDialog?.let { day ->
        val rows = state.monthOccurrences[day].orEmpty()
        AlertDialog(
            onDismissRequest = { dayDialog = null },
            title = {
                Text("${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))}")
            },
            text = {
                Column {
                    if (rows.isEmpty()) {
                        Text("Срабатываний не было")
                    }
                    rows.sortedBy { it.occurrence.scheduledAt }.forEach { row ->
                        Row(Modifier.padding(vertical = 2.dp)) {
                            Text(row.timeText, Modifier.width(52.dp))
                            Text(row.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(row.statusLabel, color = statusColor(row.statusLabel))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dayDialog = null }) { Text("Закрыть") }
            },
        )
    }

    reminderDialog?.let { stats ->
        AlertDialog(
            onDismissRequest = { reminderDialog = null },
            title = { Text(stats.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    stats.byPeriod.forEach { p ->
                        Text("${p.days} дней: выполнено ${p.done} из ${p.total} (${p.percent}%)")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Серия без пропусков: ${stats.streakDays} дн.",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Выполнено по неделям (8 недель):")
                    Spacer(Modifier.height(4.dp))
                    WeeklyBars(stats.weeklyDone)
                }
            },
            confirmButton = {
                TextButton(onClick = { reminderDialog = null }) { Text("Закрыть") }
            },
        )
    }
}

@Composable
private fun SummaryCard(state: StatsUiState) {
    val s = state.summary
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "За ${state.periodDays} дней",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatCell("Всего", s.total, MaterialTheme.colorScheme.onSurface)
                StatCell("Выполнено", s.done, Green)
                StatCell("Пропущено", s.missed, Red)
                StatCell("Вручную", s.skipped, Yellow)
                StatCell("Отложено", s.snoozed, MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(12.dp))
            Text("Дисциплина: ${s.discipline}%", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { s.discipline / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.titleMedium, color = color)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReminderStatsCard(
    stats: ReminderStats,
    periodDays: Int,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stats.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$periodDays дн.: выполнено ${stats.done} из ${stats.total} (${stats.percent}%) · " +
                        "серия ${stats.streakDays} дн.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            WeeklyBars(stats.weeklyDone, heightDp = 36)
        }
    }
}

@Composable
private fun WeeklyBars(values: List<Int>, heightDp: Int = 48) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.height(heightDp.dp),
    ) {
        values.forEach { v ->
            Box(
                Modifier
                    .width(8.dp)
                    .height((4 + (heightDp - 8) * v / max).dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

@Composable
private fun CalendarCard(
    state: StatsUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDay: (LocalDate) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onPrev) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Предыдущий месяц")
                }
                Text(
                    "${state.month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))} ${state.month.year}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Следующий месяц")
                }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { d ->
                    Text(
                        d,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            val firstDay = state.month.atDay(1)
            val lead = (firstDay.dayOfWeek.value + 6) % 7 // Пн = 0
            val daysInMonth = state.month.lengthOfMonth()
            var dayNumber = 1
            val rows = (daysInMonth + lead + 6) / 7
            repeat(rows) {
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { col ->
                        val cellIndex = it * 7 + col
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (cellIndex >= lead && dayNumber <= daysInMonth) {
                                val date = state.month.atDay(dayNumber)
                                DayCell(
                                    day = dayNumber,
                                    status = state.dayStatuses[date] ?: DayStatus.NONE,
                                    onClick = { onDay(date) },
                                )
                                dayNumber++
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(Green, "все выполнены")
                LegendDot(Yellow, "частично")
                LegendDot(Red, "были пропуски")
                LegendDot(Grey, "пусто")
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, status: DayStatus, onClick: () -> Unit) {
    val background = when (status) {
        DayStatus.NONE -> MaterialTheme.colorScheme.surfaceVariant
        DayStatus.ALL_DONE -> Green
        DayStatus.PARTIAL -> Yellow
        DayStatus.HAS_MISSED -> Red
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(2.dp)
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick),
    ) {
        Text(
            "$day",
            style = MaterialTheme.typography.bodySmall,
            color = if (status == DayStatus.NONE) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                Color(0xFF20302A)
            },
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryFilters(state: StatsUiState, viewModel: StatsViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = state.historyStatus == null,
                onClick = { viewModel.setHistoryStatus(null) },
                label = { Text("Все") },
            )
            FilterChip(
                selected = state.historyStatus == OccurrenceStatus.DONE,
                onClick = { viewModel.setHistoryStatus(OccurrenceStatus.DONE) },
                label = { Text("Выполнено") },
            )
            FilterChip(
                selected = state.historyStatus == OccurrenceStatus.MISSED,
                onClick = { viewModel.setHistoryStatus(OccurrenceStatus.MISSED) },
                label = { Text("Пропущено") },
            )
            FilterChip(
                selected = state.historyStatus == OccurrenceStatus.SKIPPED,
                onClick = { viewModel.setHistoryStatus(OccurrenceStatus.SKIPPED) },
                label = { Text("Вручную") },
            )
        }
        Box {
            TextButton(onClick = { menuOpen = true }) {
                Text(
                    if (state.historyReminderId == null) {
                        "Все напоминания"
                    } else {
                        state.historyReminderTitle
                    },
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Все напоминания") },
                    onClick = {
                        menuOpen = false
                        viewModel.setHistoryReminder(null)
                    },
                )
                state.reminderStats.forEach { stats ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(stats.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            menuOpen = false
                            viewModel.setHistoryReminder(stats.reminderId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRowCard(row: HistoryRow) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(84.dp)) {
                Text(row.dateText, style = MaterialTheme.typography.bodySmall)
                Text(
                    row.timeText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.occurrence.snoozeCount > 0) {
                    Text(
                        "откладывали ×${row.occurrence.snoozeCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                row.statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor(row.statusLabel),
            )
        }
    }
}

private fun statusColor(label: String): Color = when (label) {
    "выполнено" -> Green
    "пропущено", "пропущено вручную" -> Red
    "отложено", "ожидает" -> Yellow
    else -> Grey
}
