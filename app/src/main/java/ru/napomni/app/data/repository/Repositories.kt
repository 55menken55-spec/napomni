package ru.napomni.app.data.repository

import kotlinx.coroutines.flow.Flow
import ru.napomni.app.data.db.CategoryDao
import ru.napomni.app.data.db.NoteDao
import ru.napomni.app.data.db.ReminderDao
import ru.napomni.app.data.model.Category
import ru.napomni.app.data.model.Note
import ru.napomni.app.data.model.NoteBlock
import ru.napomni.app.data.model.NoteWithBlocks
import ru.napomni.app.data.model.Reminder
import java.time.Instant

class ReminderRepository(private val dao: ReminderDao) {
    fun observeAll(): Flow<List<Reminder>> = dao.observeAll()

    /** Все напоминания одним списком — для виджета и бэкапа (FR-10, FR-11). */
    suspend fun all(): List<Reminder> = dao.getAll()

    suspend fun get(id: Long): Reminder? = dao.getById(id)

    /** Создаёт или обновляет напоминание, возвращает id. */
    suspend fun upsert(reminder: Reminder): Long =
        if (reminder.id == 0L) {
            dao.insert(reminder)
        } else {
            dao.update(reminder)
            reminder.id
        }

    suspend fun setEnabled(id: Long, enabled: Boolean) =
        dao.setEnabled(id, enabled, Instant.now())

    suspend fun delete(reminder: Reminder) = dao.delete(reminder)
}

class NoteRepository(private val dao: NoteDao) {
    fun observeAll(): Flow<List<Note>> = dao.observeAll()

    fun observeAllWithBlocks(): Flow<List<NoteWithBlocks>> = dao.observeAllWithBlocks()

    suspend fun getWithBlocks(id: Long): NoteWithBlocks? = dao.getWithBlocks(id)

    /** Сохраняет заметку с блоками, возвращает id. */
    suspend fun upsertWithBlocks(note: Note, blocks: List<NoteBlock>): Long =
        dao.upsertNoteWithBlocks(note, blocks)

    suspend fun setPinned(id: Long, pinned: Boolean) =
        dao.setPinned(id, pinned, Instant.now())

    suspend fun setArchived(id: Long, archived: Boolean) =
        dao.setArchived(id, archived, Instant.now())

    suspend fun setCategory(id: Long, categoryId: Long?) =
        dao.setCategory(id, categoryId, Instant.now())

    suspend fun delete(note: Note) = dao.delete(note)
}

class CategoryRepository(
    private val dao: CategoryDao,
    private val noteDao: NoteDao,
    private val reminderDao: ReminderDao,
) {
    fun observeAll(): Flow<List<Category>> = dao.observeAll()

    suspend fun add(name: String, color: ru.napomni.app.data.model.NoteColor): Long =
        dao.insert(Category(name = name, color = color))

    suspend fun update(category: Category) = dao.update(category)

    /**
     * Удаляет категорию и отвязывает её от записей (ТЗ, FR-7.6:
     * «Удаление категории не удаляет записи»).
     */
    suspend fun deleteAndUnlink(category: Category) {
        noteDao.clearCategory(category.id)
        reminderDao.clearCategory(category.id)
        dao.delete(category)
    }
}
