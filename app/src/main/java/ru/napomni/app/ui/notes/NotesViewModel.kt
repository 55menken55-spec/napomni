package ru.napomni.app.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.napomni.app.data.model.Note
import ru.napomni.app.data.model.NoteBlockType
import ru.napomni.app.data.model.NoteWithBlocks
import ru.napomni.app.data.repository.NoteRepository

/** Строка списка заметок. */
data class NoteListItem(
    val note: Note,
    val preview: String,
    /** «3/5» — прогресс чек-листа, если есть пункты. */
    val checkInfo: String?,
)

data class NotesUiState(
    val pinned: List<NoteListItem> = emptyList(),
    val others: List<NoteListItem> = emptyList(),
    val archived: List<NoteListItem> = emptyList(),
    val showArchived: Boolean = false,
)

class NotesViewModel(private val repository: NoteRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val showArchived = MutableStateFlow(false)

    val uiState: StateFlow<NotesUiState> = combine(
        repository.observeAllWithBlocks(),
        query,
        showArchived,
    ) { notes, text, archivedVisible ->
        buildState(notes, text.trim(), archivedVisible)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    private fun buildState(
        notes: List<NoteWithBlocks>,
        queryText: String,
        archivedVisible: Boolean,
    ): NotesUiState {
        val filtered = notes.filter { item ->
            if (queryText.isBlank()) {
                true
            } else {
                item.matches(queryText.lowercase())
            }
        }
        val toListItem: (NoteWithBlocks) -> NoteListItem = { NoteListItem(it.note, it.preview(), it.checkInfo()) }
        return NotesUiState(
            pinned = filtered.filter { it.note.pinned && !it.note.archived }.map(toListItem),
            others = filtered.filter { !it.note.pinned && !it.note.archived }.map(toListItem),
            archived = filtered.filter { it.note.archived }.map(toListItem),
            showArchived = archivedVisible,
        )
    }

    fun setQuery(text: String) {
        _query.value = text
    }

    fun toggleArchivedVisible() {
        showArchived.value = !showArchived.value
    }

    fun setPinned(note: Note, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(note.id, pinned) }
    }

    fun setArchived(note: Note, archived: Boolean) {
        viewModelScope.launch { repository.setArchived(note.id, archived) }
    }

    fun delete(note: Note) {
        viewModelScope.launch { repository.delete(note) }
    }

    // --- Пакетные действия (режим выделения, ТЗ, FR-7.5) ---

    fun setPinnedMany(ids: Set<Long>, pinned: Boolean) {
        viewModelScope.launch {
            ids.forEach { repository.setPinned(it, pinned) }
        }
    }

    fun setArchivedMany(ids: Set<Long>, archived: Boolean) {
        viewModelScope.launch {
            ids.forEach { repository.setArchived(it, archived) }
        }
    }

    fun setCategoryMany(ids: Set<Long>, categoryId: Long?) {
        viewModelScope.launch {
            ids.forEach { repository.setCategory(it, categoryId) }
        }
    }

    fun deleteMany(ids: Set<Long>) {
        viewModelScope.launch {
            ids.forEach { id ->
                repository.getWithBlocks(id)?.let { repository.delete(it.note) }
            }
        }
    }
}

// --- вспомогательные функции ---

private fun NoteWithBlocks.matches(lowerQuery: String): Boolean {
    if (note.title.lowercase().contains(lowerQuery)) return true
    return blocks.any { it.text.lowercase().contains(lowerQuery) }
}

private fun NoteWithBlocks.preview(): String {
    val firstText = blocks
        .filter { it.type == NoteBlockType.TEXT && it.text.isNotBlank() }
        .firstOrNull()
        ?.text
    if (firstText != null) return firstText
    val checkText = blocks.firstOrNull { it.text.isNotBlank() }?.text
    return checkText ?: ""
}

private fun NoteWithBlocks.checkInfo(): String? {
    val checks = blocks.filter { it.type == NoteBlockType.CHECK }
    if (checks.isEmpty()) return null
    return "${checks.count { it.done }}/${checks.size}"
}
