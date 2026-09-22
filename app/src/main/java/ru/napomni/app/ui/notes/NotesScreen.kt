package ru.napomni.app.ui.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.napomni.app.data.model.Category
import ru.napomni.app.data.model.Note
import ru.napomni.app.ui.AppViewModelProvider
import ru.napomni.app.ui.categories.CategoriesViewModel
import ru.napomni.app.ui.components.CategoryChooseDialog
import ru.napomni.app.ui.components.CategoryDot
import ru.napomni.app.ui.components.CategoryManagerDialog
import ru.napomni.app.ui.components.ConfirmDialog
import ru.napomni.app.ui.components.SectionHeader
import ru.napomni.app.ui.theme.composeColor

private const val FILTER_ALL = "all"
private const val FILTER_NONE = "none"

/**
 * Список заметок (ТЗ, FR-7): поиск, фильтр по категориям, закреплённые, архив,
 * свайп (в архив / закрепить), долгое нажатие — режим выделения.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(
    onOpenNote: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotesViewModel = viewModel(factory = AppViewModelProvider.Factory),
    categoriesViewModel: CategoriesViewModel = viewModel(
        factory = AppViewModelProvider.Factory,
        key = "categories",
    ),
) {
    val state by viewModel.uiState.collectAsState()
    val queryText by viewModel.query.collectAsState()
    val categories by categoriesViewModel.categories.collectAsState()

    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showCategoryManager by remember { mutableStateOf(false) }
    var showBatchCategory by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<Note?>(null) }
    var batchDelete by remember { mutableStateOf(false) }

    fun matchesFilter(item: NoteListItem): Boolean = when (filter) {
        FILTER_ALL -> true
        FILTER_NONE -> item.note.categoryId == null
        else -> item.note.categoryId == filter.toLongOrNull()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (selectedIds.isEmpty()) {
                TopAppBar(
                    title = { Text("Заметки") },
                    actions = {
                        IconButton(onClick = { showCategoryManager = true }) {
                            Icon(Icons.Default.Folder, contentDescription = "Категории")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Выбрано: ${selectedIds.size}") },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Снять выделение")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            viewModel.setPinnedMany(selectedIds, true)
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.PushPin, contentDescription = "Закрепить")
                        }
                        IconButton(onClick = {
                            viewModel.setArchivedMany(selectedIds, true)
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Archive, contentDescription = "В архив")
                        }
                        IconButton(onClick = { showBatchCategory = true }) {
                            Icon(Icons.Default.Folder, contentDescription = "Категория")
                        }
                        IconButton(onClick = { batchDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить")
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = queryText,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text("Поиск по заметкам") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )

                if (categories.isNotEmpty()) {
                    FilterRow(
                        categories = categories,
                        filter = filter,
                        onFilter = { filter = it },
                    )
                }

                val pinned = state.pinned.filter(::matchesFilter)
                val others = state.others.filter(::matchesFilter)
                val archived = state.archived.filter(::matchesFilter)

                if (pinned.isEmpty() && others.isEmpty() && archived.isEmpty() &&
                    !state.showArchived
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Заметок пока нет", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Нажмите «+», чтобы оставить себе заметку\nили список дел с галочками.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = 88.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (pinned.isNotEmpty()) {
                            item { SectionHeader("Закреплённые") }
                            items(pinned, key = { "pinned-${it.note.id}" }) { item ->
                                SwipeableNoteCard(
                                    item = item,
                                    selected = item.note.id in selectedIds,
                                    onOpen = { onOpenNote(item.note.id) },
                                    onToggleSelect = {
                                        selectedIds = if (item.note.id in selectedIds) {
                                            selectedIds - item.note.id
                                        } else {
                                            selectedIds + item.note.id
                                        }
                                    },
                                    onTogglePin = {
                                        viewModel.setPinned(item.note, !item.note.pinned)
                                    },
                                    onArchive = {
                                        viewModel.setArchived(item.note, !item.note.archived)
                                    },
                                    onDelete = { deleteCandidate = item.note },
                                    categories = categories,
                                )
                            }
                        }
                        if (others.isNotEmpty()) {
                            if (pinned.isNotEmpty()) {
                                item { SectionHeader("Заметки") }
                            }
                            items(others, key = { "note-${it.note.id}" }) { item ->
                                SwipeableNoteCard(
                                    item = item,
                                    selected = item.note.id in selectedIds,
                                    onOpen = { onOpenNote(item.note.id) },
                                    onToggleSelect = {
                                        selectedIds = if (item.note.id in selectedIds) {
                                            selectedIds - item.note.id
                                        } else {
                                            selectedIds + item.note.id
                                        }
                                    },
                                    onTogglePin = {
                                        viewModel.setPinned(item.note, !item.note.pinned)
                                    },
                                    onArchive = {
                                        viewModel.setArchived(item.note, !item.note.archived)
                                    },
                                    onDelete = { deleteCandidate = item.note },
                                    categories = categories,
                                )
                            }
                        }
                        item {
                            TextButton(onClick = { viewModel.toggleArchivedVisible() }) {
                                Icon(
                                    Icons.Default.Archive,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (state.showArchived) {
                                        "Скрыть архив"
                                    } else {
                                        "Архив (${archived.size})"
                                    },
                                )
                            }
                        }
                        if (state.showArchived) {
                            items(archived, key = { "arch-${it.note.id}" }) { item ->
                                SwipeableNoteCard(
                                    item = item,
                                    selected = item.note.id in selectedIds,
                                    onOpen = { onOpenNote(item.note.id) },
                                    onToggleSelect = {
                                        selectedIds = if (item.note.id in selectedIds) {
                                            selectedIds - item.note.id
                                        } else {
                                            selectedIds + item.note.id
                                        }
                                    },
                                    onTogglePin = {
                                        viewModel.setPinned(item.note, !item.note.pinned)
                                    },
                                    onArchive = {
                                        viewModel.setArchived(item.note, !item.note.archived)
                                    },
                                    onDelete = { deleteCandidate = item.note },
                                    categories = categories,
                                )
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { onOpenNote(-1L) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить заметку")
            }
        }
    }

    if (showCategoryManager) {
        CategoryManagerDialog(
            categories = categories,
            onAdd = categoriesViewModel::add,
            onUpdate = categoriesViewModel::update,
            onDelete = categoriesViewModel::delete,
            onDismiss = { showCategoryManager = false },
        )
    }

    if (showBatchCategory) {
        CategoryChooseDialog(
            categories = categories,
            onChoose = { categoryId ->
                viewModel.setCategoryMany(selectedIds, categoryId)
                selectedIds = emptySet()
            },
            onDismiss = { showBatchCategory = false },
        )
    }

    deleteCandidate?.let { note ->
        ConfirmDialog(
            title = "Удалить заметку?",
            text = "«${note.title.ifBlank { "Без названия" }}» будет удалена без возможности восстановления.",
            onConfirm = { viewModel.delete(note) },
            onDismiss = { deleteCandidate = null },
        )
    }

    if (batchDelete) {
        ConfirmDialog(
            title = "Удалить ${selectedIds.size} заметок?",
            text = "Заметки будут удалены без возможности восстановления.",
            onConfirm = {
                viewModel.deleteMany(selectedIds)
                selectedIds = emptySet()
            },
            onDismiss = { batchDelete = false },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(
    categories: List<Category>,
    filter: String,
    onFilter: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            androidx.compose.material3.FilterChip(
                selected = filter == FILTER_ALL,
                onClick = { onFilter(FILTER_ALL) },
                label = { Text("Все") },
            )
            androidx.compose.material3.FilterChip(
                selected = filter == FILTER_NONE,
                onClick = { onFilter(FILTER_NONE) },
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
}

/** Заметка со свайпами: влево — в архив, вправо — закрепить (ТЗ, FR-7.5). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SwipeableNoteCard(
    item: NoteListItem,
    selected: Boolean,
    categories: List<Category>,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onTogglePin: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> onArchive()
                SwipeToDismissBoxValue.StartToEnd -> onTogglePin()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false // всегда возвращаем карточку — список сам обновится из БД
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val alignment = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.CenterEnd
            }
            Box(Modifier.fillMaxSize(), contentAlignment = alignment) {
                Icon(
                    if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                        Icons.Default.PushPin
                    } else {
                        Icons.Default.Archive
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        },
    ) {
        NoteCard(
            item = item,
            categories = categories,
            selected = selected,
            onOpen = onOpen,
            onLongPress = onToggleSelect,
            onTogglePin = onTogglePin,
            onArchive = onArchive,
            onDelete = onDelete,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    item: NoteListItem,
    categories: List<Category>,
    selected: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onTogglePin: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val note = item.note
    val category = categories.firstOrNull { it.id == note.categoryId }

    Card(
        modifier = Modifier.combinedClickable(
            onClick = onOpen,
            onLongClick = onLongPress,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(note.color.composeColor()),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        note.title.ifBlank { "Без названия" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (note.pinned) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Закреплена",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (item.preview.isNotBlank()) {
                    Text(
                        item.preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.checkInfo != null) {
                        Text(
                            "Чек-лист: ${item.checkInfo}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (category != null) {
                        Spacer(Modifier.width(8.dp))
                        CategoryDot(category, size = 8)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            category.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (!selected) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Ещё")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(if (note.pinned) "Открепить" else "Закрепить")
                            },
                            leadingIcon = {
                                Icon(Icons.Default.PushPin, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onTogglePin()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (note.archived) "Вернуть из архива" else "В архив")
                            },
                            leadingIcon = {
                                Icon(
                                    if (note.archived) {
                                        Icons.Default.Unarchive
                                    } else {
                                        Icons.Default.Archive
                                    },
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onArchive()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить") },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            },
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
}
