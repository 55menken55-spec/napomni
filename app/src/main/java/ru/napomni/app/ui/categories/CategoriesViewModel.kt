package ru.napomni.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.napomni.app.data.model.Category
import ru.napomni.app.data.model.NoteColor
import ru.napomni.app.data.repository.CategoryRepository

/** Управление категориями (общие для заметок и напоминаний, ТЗ, FR-7.6). */
class CategoriesViewModel(private val repository: CategoryRepository) : ViewModel() {

    val categories: StateFlow<List<Category>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(name: String, color: NoteColor) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.add(trimmed, color) }
    }

    fun update(category: Category, name: String, color: NoteColor) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.update(category.copy(name = trimmed, color = color))
        }
    }

    /** Удаление категории: записи сохраняются, просто теряют привязку (FR-7.6). */
    fun delete(category: Category) {
        viewModelScope.launch { repository.deleteAndUnlink(category) }
    }
}
