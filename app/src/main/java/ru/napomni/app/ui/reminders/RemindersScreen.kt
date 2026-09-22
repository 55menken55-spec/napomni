package ru.napomni.app.ui.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.napomni.app.data.model.Reminder
import ru.napomni.app.data.model.ReminderImportance
import ru.napomni.app.domain.formatTime
import ru.napomni.app.ui.AppViewModelProvider
import ru.napomni.app.ui.categories.CategoriesViewModel
import ru.napomni.app.ui.components.CategoryDot
import ru.napomni.app.ui.components.ConfirmDialog
import ru.napomni.app.ui.components.SectionHeader
import ru.napomni.app.ui.theme.composeColor

@Composable
fun RemindersScreen(
    onOpenReminder: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RemindersViewModel = viewModel(factory = AppViewModelProvider.Factory),
    categoriesViewModel: CategoriesViewModel = viewModel(
        factory = AppViewModelProvider.Factory,
        key = "categories",
    ),
) {
    val state by viewModel.uiState.collectAsState()
    val categories by categoriesViewModel.categories.collectAsState()
    var deleteCandidate by remember { mutableStateOf<Reminder?>(null) }
    var filter by rememberSaveable { mutableStateOf("all") }
    val appContext = LocalContext.current

    // Виджет «Ближайшее» — обновить при изменении данных (ТЗ: FR-11).
    LaunchedEffect(state.items, state.today) {
        ru.napomni.app.ui.widget.WidgetUpdater.update(appContext)
    }

    fun keep(categoryId: Long?): Boolean = when (filter) {
        "all" -> true
        "none" -> categoryId == null
        else -> categoryId == filter.toLongOrNull()
    }
    val today = state.today.filter { keep(it.categoryId) }
    val items = state.items.filter { keep(it.reminder.categoryId) }

    Box(modifier = modifier.fillMaxSize()) {
        if (state.items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Напоминаний пока нет",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Нажмите «+», чтобы создать первое напоминание.\nНапример: «Выпить таблетки в 15:00».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (categories.isNotEmpty()) {
                    item(key = "filter") {
                        ReminderFilterRow(
                            categories = categories,
                            filter = filter,
                            onFilter = { filter = it },
                        )
                    }
                }
                if (today.isNotEmpty()) {
                    item { SectionHeader("Сегодня") }
                    items(today, key = { "today-${it.reminderId}-${it.time}" }) { entry ->
                        TodayCard(entry)
                    }
                    item { SectionHeader("Все напоминания") }
                } else {
                    item { SectionHeader("Напоминания") }
                }
                items(items, key = { "reminder-${it.reminder.id}" }) { item ->
                    ReminderCard(
                        item = item,
                        onOpen = { onOpenReminder(item.reminder.id) },
                        onToggle = { viewModel.setEnabled(item.reminder, it) },
                        onDelete = { deleteCandidate = item.reminder },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { onOpenReminder(-1L) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Добавить напоминание")
        }

        deleteCandidate?.let { reminder ->
            ConfirmDialog(
                title = "Удалить напоминание?",
                text = "«${reminder.title}» будет удалено без возможности восстановления. " +
                    "История срабатываний сохранится в статистике.",
                onConfirm = { viewModel.delete(reminder) },
                onDismiss = { deleteCandidate = null },
            )
        }
    }
}

@Composable
private fun TodayCard(entry: TodayEntry) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatTime(entry.time.toLocalTime()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (entry.past) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(entry.color.composeColor()),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (entry.past) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (entry.past) {
                    Text(
                        "время прошло",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = if (entry.importance == ReminderImportance.ALARM) {
                    Icons.Default.Alarm
                } else {
                    Icons.Default.Notifications
                },
                contentDescription = if (entry.importance == ReminderImportance.ALARM) {
                    "Важное (как будильник)"
                } else {
                    "Уведомление"
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReminderCard(
    item: ReminderListItem,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val reminder = item.reminder

    Card(onClick = onOpen) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(reminder.color.composeColor()),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.scheduleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.nextText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (reminder.importance == ReminderImportance.ALARM) {
                    Icons.Default.Alarm
                } else {
                    Icons.Default.Notifications
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(
                checked = reminder.enabled,
                onCheckedChange = onToggle,
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Ещё")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Удалить") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}


/** Фильтр по категориям (ТЗ, FR-7.6). */
@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
@Composable
private fun ReminderFilterRow(
    categories: List<ru.napomni.app.data.model.Category>,
    filter: String,
    onFilter: (String) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        androidx.compose.material3.FilterChip(
            selected = filter == "all",
            onClick = { onFilter("all") },
            label = { Text("Все") },
        )
        androidx.compose.material3.FilterChip(
            selected = filter == "none",
            onClick = { onFilter("none") },
            label = { Text("Без категории") },
        )
        categories.forEach { category ->
            androidx.compose.material3.FilterChip(
                selected = filter == category.id.toString(),
                onClick = { onFilter(category.id.toString()) },
                leadingIcon = { CategoryDot(category) },
                label = { Text(category.name) },
            )
        }
    }
}
