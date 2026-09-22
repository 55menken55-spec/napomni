package ru.napomni.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ru.napomni.app.data.model.Category
import ru.napomni.app.data.model.NoteColor
import ru.napomni.app.ui.theme.composeColor

/**
 * Переиспользуемые компоненты категорий (ТЗ, FR-7.6).
 * Родитель передаёт данные и колбэки — компоненты без состояния данных.
 */

/** Строка-фильтр выбора категории (включая «Без категории» и «+ Новая»). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPickerRow(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedId == null,
            onClick = { onSelect(null) },
            label = { Text("Без категории") },
        )
        categories.forEach { category ->
            FilterChip(
                selected = selectedId == category.id,
                onClick = { onSelect(category.id) },
                leadingIcon = { CategoryDot(category) },
                label = { Text(category.name) },
            )
        }
        FilterChip(
            selected = false,
            onClick = onAddClick,
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
            label = { Text("Новая") },
        )
    }
}

@Composable
fun CategoryDot(category: Category, size: Int = 12) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(category.color.composeColor()),
    )
}

/** Диалог создания/редактирования категории: имя + цвет. */
@Composable
fun CategoryEditDialog(
    category: Category?,
    onSave: (name: String, color: NoteColor) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var color by remember { mutableStateOf(category?.color ?: NoteColor.TEAL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (category == null) "Новая категория" else "Изменить категорию")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название *") },
                    placeholder = { Text("Например: Лекарства") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                FieldLabel("Цвет")
                ColorDotPicker(selected = color, onSelect = { color = it })
                if (onDelete != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        onDelete()
                        onDismiss()
                    }) {
                        Text("Удалить категорию", color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        "Заметки и напоминания останутся — они просто потеряют привязку.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(name.trim(), color)
                    onDismiss()
                },
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/** Диалог управления списком категорий. */
@Composable
fun CategoryManagerDialog(
    categories: List<Category>,
    onAdd: (name: String, color: NoteColor) -> Unit,
    onUpdate: (Category, name: String, color: NoteColor) -> Unit,
    onDelete: (Category) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Category?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Категории") },
        text = {
            Column {
                if (categories.isEmpty()) {
                    Text(
                        "Категорий пока нет. Создайте — например, «Лекарства», «Дом», «Работа».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                categories.forEach { category ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        CategoryDot(category)
                        Spacer(Modifier.width(10.dp))
                        Text(category.name, Modifier.weight(1f))
                        IconButton(onClick = { editing = category }) {
                            Icon(Icons.Default.Edit, contentDescription = "Изменить")
                        }
                        IconButton(onClick = { deleting = category }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить")
                        }
                    }
                }
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Добавить категорию")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Готово") }
        },
    )

    if (creating) {
        CategoryEditDialog(
            category = null,
            onSave = { name, color -> onAdd(name, color) },
            onDismiss = { creating = false },
        )
    }
    editing?.let { category ->
        CategoryEditDialog(
            category = category,
            onSave = { name, color -> onUpdate(category, name, color) },
            onDelete = { onDelete(category) },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { category ->
        ConfirmDialog(
            title = "Удалить категорию?",
            text = "«${category.name}» будет удалена. Заметки и напоминания останутся — " +
                "они просто потеряют привязку к категории.",
            confirmLabel = "Удалить",
            onConfirm = { onDelete(category) },
            onDismiss = { deleting = null },
        )
    }
}

/** Выбор категории для пакетных действий (режим выделения). */
@Composable
fun CategoryChooseDialog(
    categories: List<Category>,
    onChoose: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменить категорию") },
        text = {
            Column {
                TextButton(onClick = {
                    onChoose(null)
                    onDismiss()
                }) { Text("Без категории") }
                categories.forEach { category ->
                    androidx.compose.material3.TextButton(onClick = {
                        onChoose(category.id)
                        onDismiss()
                    }) {
                        CategoryDot(category)
                        Spacer(Modifier.width(10.dp))
                        Text(category.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
