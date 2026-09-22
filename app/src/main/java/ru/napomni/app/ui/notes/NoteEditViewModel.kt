package ru.napomni.app.ui.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.napomni.app.data.model.Note
import ru.napomni.app.data.model.NoteBlock
import ru.napomni.app.data.model.NoteBlockType
import ru.napomni.app.data.model.NoteColor
import ru.napomni.app.data.repository.NoteRepository
import java.time.Instant

/**
 * Блок заметки в форме редактирования.
 * [uid] — стабильный ключ для drag-reorder (переживает пересоздание id в БД).
 */
data class BlockForm(
    val uid: Long,
    val id: Long = 0,
    val type: NoteBlockType,
    val text: String = "",
    val done: Boolean = false,
)

data class NoteFormState(
    val id: Long = 0,
    val title: String = "",
    val color: NoteColor = NoteColor.YELLOW,
    val categoryId: Long? = null,
    val pinned: Boolean = false,
    val blocks: List<BlockForm> = listOf(BlockForm(uid = 0L, type = NoteBlockType.TEXT)),
    val error: String? = null,
)

class NoteEditViewModel(
    private val repository: NoteRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val noteId: Long = savedStateHandle.get<Long>("id") ?: -1L
    private var nextUid = 1L

    private val _state = MutableStateFlow(NoteFormState())
    val state: StateFlow<NoteFormState> = _state.asStateFlow()

    init {
        if (noteId > 0) {
            viewModelScope.launch {
                repository.getWithBlocks(noteId)?.let { item ->
                    _state.update {
                        NoteFormState(
                            id = item.note.id,
                            title = item.note.title,
                            color = item.note.color,
                            categoryId = item.note.categoryId,
                            pinned = item.note.pinned,
                            blocks = item.blocks
                                .sortedBy { block -> block.orderIndex }
                                .map { block ->
                                    BlockForm(
                                        uid = nextUid++,
                                        id = block.id,
                                        type = block.type,
                                        text = block.text,
                                        done = block.done,
                                    )
                                }
                                .ifEmpty { listOf(BlockForm(uid = 0L, type = NoteBlockType.TEXT)) },
                        )
                    }
                }
            }
        }
    }

    fun update(transform: (NoteFormState) -> NoteFormState) {
        _state.update { transform(it).copy(error = null) }
    }

    fun setTitle(title: String) = update { it.copy(title = title) }

    fun setColor(color: NoteColor) = update { it.copy(color = color) }

    fun setCategory(categoryId: Long?) = update { it.copy(categoryId = categoryId) }

    fun togglePinned() = update { it.copy(pinned = !it.pinned) }

    fun addBlock(type: NoteBlockType) = update {
        it.copy(blocks = it.blocks + BlockForm(uid = nextUid++, type = type))
    }

    fun updateBlockText(uid: Long, text: String) = updateBlocks { blocks ->
        blocks.map { block -> if (block.uid == uid) block.copy(text = text) else block }
    }

    fun toggleBlockDone(uid: Long) = updateBlocks { blocks ->
        blocks.map { block -> if (block.uid == uid) block.copy(done = !block.done) else block }
    }

    /** Перемещение блока drag-reorder (по uid). */
    fun moveBlockByUid(fromUid: Long, toUid: Long) = updateBlocks { blocks ->
        val from = blocks.indexOfFirst { it.uid == fromUid }
        val to = blocks.indexOfFirst { it.uid == toUid }
        if (from == -1 || to == -1 || from == to) {
            blocks
        } else {
            blocks.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    fun removeBlock(uid: Long) = updateBlocks { blocks ->
        val remaining = blocks.filterNot { it.uid == uid }
        if (remaining.isEmpty()) listOf(BlockForm(uid = 0L, type = NoteBlockType.TEXT)) else remaining
    }

    private fun updateBlocks(transform: (List<BlockForm>) -> List<BlockForm>) =
        update { it.copy(blocks = transform(it.blocks)) }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val form = _state.value
            val hasContent = form.title.isNotBlank() ||
                form.blocks.any { it.text.isNotBlank() }
            if (!hasContent) {
                _state.update { it.copy(error = "Заметка пустая — добавьте текст") }
                return@launch
            }
            val now = Instant.now()
            val existing = if (form.id > 0) repository.getWithBlocks(form.id) else null
            val note = Note(
                id = form.id,
                title = form.title.trim(),
                color = form.color,
                categoryId = form.categoryId,
                pinned = form.pinned,
                archived = existing?.note?.archived ?: false,
                createdAt = existing?.note?.createdAt ?: now,
                updatedAt = now,
            )
            val blocks = form.blocks
                .mapIndexed { index, block ->
                    NoteBlock(
                        noteId = 0, // подставит DAO при сохранении
                        type = block.type,
                        text = block.text,
                        done = block.done,
                        orderIndex = index,
                    )
                }
                .filter { it.text.isNotBlank() }
            repository.upsertWithBlocks(note, blocks)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val form = _state.value
            if (form.id > 0) {
                repository.getWithBlocks(form.id)?.let { repository.delete(it.note) }
            }
            onDone()
        }
    }
}
