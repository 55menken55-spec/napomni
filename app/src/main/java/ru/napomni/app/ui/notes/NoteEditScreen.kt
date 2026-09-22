package ru.napomni.app.ui.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.napomni.app.data.model.NoteBlockType
import ru.napomni.app.ui.AppViewModelProvider
import ru.napomni.app.ui.categories.CategoriesViewModel
import ru.napomni.app.ui.components.CategoryEditDialog
import ru.napomni.app.ui.components.CategoryPickerRow
import ru.napomni.app.ui.components.ColorDotPicker
import ru.napomni.app.ui.components.SectionHeader
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Редактор заметки (ТЗ, FR-7): текст + чек-лист, drag-reorder блоков за ручку,
 * сворачивание выполненных, цвет, категория, закрепление.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteEditScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoteEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
    categoriesViewModel: CategoriesViewModel = viewModel(
        factory = AppViewModelProvider.Factory,
        key = "categories",
    ),
) {
    val state by viewModel.state.collectAsState()
    val categories by categoriesViewModel.categories.collectAsState()
    var showDelete by remember { mutableStateOf(false) }
    var hideDone by remember { mutableStateOf(false) }
    var showCategoryCreate by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromUid = from.key as? Long ?: return@rememberReorderableLazyListState
        val toUid = to.key as? Long ?: return@rememberReorderableLazyListState
        viewModel.moveBlockByUid(fromUid, toUid)
    }

    val doneCount = state.blocks.count { it.type == NoteBlockType.CHECK && it.done }
    val visibleBlocks = if (hideDone) {
        state.blocks.filterNot { it.type == NoteBlockType.CHECK && it.done }
    } else {
        state.blocks
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (state.id == 0L) "Новая заметка" else "Заметка") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.togglePinned() }) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = if (state.pinned) "Открепить" else "Закрепить",
                            tint = if (state.pinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    TextButton(onClick = { viewModel.save(onDone) }) {
                        Text("Готово")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "title") {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    label = { Text("Заголовок") },
                    placeholder = { Text("Например: Список покупок") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "blocks-header") {
                SectionHeader("Содержимое")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.addBlock(NoteBlockType.TEXT) }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Текст")
                    }
                    OutlinedButton(onClick = { viewModel.addBlock(NoteBlockType.CHECK) }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Пункт списка")
                    }
                }
            }

            if (doneCount > 0) {
                item(key = "collapse-done") {
                    TextButton(onClick = { hideDone = !hideDone }) {
                        Text(
                            if (hideDone) {
                                "Показать выполненные ($doneCount)"
                            } else {
                                "Скрыть выполненные ($doneCount)"
                            },
                        )
                    }
                }
            }

            items(visibleBlocks, key = { it.uid }) { block ->
                ReorderableItem(reorderState, key = block.uid) { isDragging ->
                    BlockRow(
                        block = block,
                        isDragging = isDragging,
                        handleModifier = Modifier.longPressDraggableHandle(),
                        onTextChange = { viewModel.updateBlockText(block.uid, it) },
                        onToggleDone = { viewModel.toggleBlockDone(block.uid) },
                        onRemove = { viewModel.removeBlock(block.uid) },
                    )
                }
            }

            item(key = "category") {
                SectionHeader("Категория")
                CategoryPickerRow(
                    categories = categories,
                    selectedId = state.categoryId,
                    onSelect = viewModel::setCategory,
                    onAddClick = { showCategoryCreate = true },
                )
            }

            item(key = "color") {
                SectionHeader("Цвет метки")
                ColorDotPicker(
                    selected = state.color,
                    onSelect = viewModel::setColor,
                )
            }

            state.error?.let { error ->
                item(key = "error") {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (state.id > 0) {
                item(key = "delete") {
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = { showDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Удалить заметку", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showCategoryCreate) {
        CategoryEditDialog(
            category = null,
            onSave = { name, color ->
                categoriesViewModel.add(name, color)
            },
            onDismiss = { showCategoryCreate = false },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Удалить заметку?") },
            text = { Text("Заметка будет удалена без возможности восстановления.") },
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

@Composable
private fun BlockRow(
    block: BlockForm,
    isDragging: Boolean,
    handleModifier: Modifier,
    onTextChange: (String) -> Unit,
    onToggleDone: () -> Unit,
    onRemove: () -> Unit,
) {
    val background = if (isDragging) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(vertical = 2.dp),
    ) {
        // Ручка перетаскивания — long-press drag (ТЗ, FR-7.2).
        Icon(
            Icons.Default.DragHandle,
            contentDescription = "Перетащите, чтобы изменить порядок",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(22.dp)
                .then(handleModifier),
        )
        Spacer(Modifier.width(4.dp))
        if (block.type == NoteBlockType.CHECK) {
            Checkbox(checked = block.done, onCheckedChange = { onToggleDone() })
        }
        OutlinedTextField(
            value = block.text,
            onValueChange = onTextChange,
            placeholder = {
                Text(
                    if (block.type == NoteBlockType.CHECK) {
                        "Пункт списка"
                    } else {
                        "Текст заметки"
                    },
                )
            },
            minLines = if (block.type == NoteBlockType.TEXT) 2 else 1,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Удалить блок")
        }
    }
}
