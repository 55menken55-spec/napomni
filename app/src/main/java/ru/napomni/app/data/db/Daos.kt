package ru.napomni.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.napomni.app.data.model.Category
import ru.napomni.app.data.model.Note
import ru.napomni.app.data.model.NoteBlock
import ru.napomni.app.data.model.NoteWithBlocks
import ru.napomni.app.data.model.Occurrence
import ru.napomni.app.data.model.OccurrenceStatus
import ru.napomni.app.data.model.Reminder
import java.time.Instant

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY enabled DESC, title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): Reminder?

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<Reminder>

    @Insert
    suspend fun insertAll(reminders: List<Reminder>)

    @Query("DELETE FROM reminders")
    suspend fun clearAll()

    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Query("UPDATE reminders SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Instant)

    /** Отвязка категории при её удалении (ТЗ, FR-7.6: записи не удаляются). */
    @Query("UPDATE reminders SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: Long)

    @Delete
    suspend fun delete(reminder: Reminder)
}

@Dao
abstract class NoteDao {
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC")
    abstract fun observeAll(): Flow<List<Note>>

    @Transaction
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC")
    abstract fun observeAllWithBlocks(): Flow<List<NoteWithBlocks>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    abstract suspend fun getWithBlocks(id: Long): NoteWithBlocks?

    @Query("SELECT * FROM notes")
    abstract suspend fun getAllNotes(): List<Note>

    @Query("SELECT * FROM note_blocks")
    abstract suspend fun getAllBlocks(): List<NoteBlock>

    @Insert
    abstract suspend fun insert(note: Note): Long

    @Insert
    abstract suspend fun insertAllNotes(notes: List<Note>)

    @Insert
    abstract suspend fun insertAllBlocks(blocks: List<NoteBlock>)

    @Query("DELETE FROM note_blocks")
    abstract suspend fun clearAllBlocks()

    @Query("DELETE FROM notes")
    abstract suspend fun clearAllNotes()

    @Update
    abstract suspend fun update(note: Note)

    @Delete
    abstract suspend fun delete(note: Note)

    @Query("UPDATE notes SET pinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
    abstract suspend fun setPinned(id: Long, pinned: Boolean, updatedAt: Instant)

    @Query("UPDATE notes SET archived = :archived, updatedAt = :updatedAt WHERE id = :id")
    abstract suspend fun setArchived(id: Long, archived: Boolean, updatedAt: Instant)

    @Query("UPDATE notes SET categoryId = :categoryId, updatedAt = :updatedAt WHERE id = :id")
    abstract suspend fun setCategory(id: Long, categoryId: Long?, updatedAt: Instant)

    /** Отвязка категории при её удалении (ТЗ, FR-7.6). */
    @Query("UPDATE notes SET categoryId = NULL WHERE categoryId = :categoryId")
    abstract suspend fun clearCategory(categoryId: Long)

    @Insert
    abstract suspend fun insertBlock(block: NoteBlock): Long

    @Query("DELETE FROM note_blocks WHERE noteId = :noteId")
    abstract suspend fun deleteBlocksOf(noteId: Long)

    /** Сохраняет заметку целиком вместе с блоками (блоки пересоздаются с новыми id). */
    @Transaction
    open suspend fun upsertNoteWithBlocks(note: Note, blocks: List<NoteBlock>): Long {
        val noteId = if (note.id == 0L) {
            insert(note)
        } else {
            update(note)
            note.id
        }
        deleteBlocksOf(noteId)
        blocks.forEach { insertBlock(it.copy(id = 0, noteId = noteId)) }
        return noteId
    }
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<Category>

    @Insert
    suspend fun insert(category: Category): Long

    @Insert
    suspend fun insertAll(categories: List<Category>)

    @Query("DELETE FROM categories")
    suspend fun clearAll()

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)
}

@Dao
interface OccurrenceDao {
    @Query("SELECT * FROM occurrences")
    suspend fun getAll(): List<Occurrence>

    @Insert
    suspend fun insert(occurrence: Occurrence): Long

    @Insert
    suspend fun insertAll(occurrences: List<Occurrence>)

    @Query("DELETE FROM occurrences")
    suspend fun clearAll()

    @Query("SELECT * FROM occurrences WHERE id = :id")
    suspend fun getById(id: Long): Occurrence?

    @Query("SELECT * FROM occurrences WHERE reminderId = :reminderId AND scheduledAt = :scheduledAt LIMIT 1")
    suspend fun find(reminderId: Long, scheduledAt: Instant): Occurrence?

    /** Активные (незакрытые) срабатывания напоминания — для отмены будильников. */
    @Query("SELECT * FROM occurrences WHERE reminderId = :reminderId AND status IN ('PENDING', 'SNOOZED')")
    suspend fun activeFor(reminderId: Long): List<Occurrence>

    @Update
    suspend fun update(occurrence: Occurrence)

    @Query("SELECT * FROM occurrences WHERE scheduledAt BETWEEN :from AND :to ORDER BY scheduledAt ASC")
    suspend fun between(from: Instant, to: Instant): List<Occurrence>

    @Query("SELECT * FROM occurrences WHERE scheduledAt BETWEEN :from AND :to ORDER BY scheduledAt ASC")
    fun observeBetween(from: Instant, to: Instant): Flow<List<Occurrence>>

    @Query("SELECT * FROM occurrences WHERE reminderId = :reminderId ORDER BY scheduledAt DESC")
    fun observeForReminder(reminderId: Long): Flow<List<Occurrence>>

    @Query("UPDATE occurrences SET status = :status, completedAt = :completedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: OccurrenceStatus, completedAt: Instant?)
}

/** Находит или создаёт запись срабатывания (без дублей благодаря уникальному индексу). */
suspend fun OccurrenceDao.getOrCreate(
    reminderId: Long,
    scheduledAt: Instant,
    status: OccurrenceStatus,
    title: String = "",
    color: ru.napomni.app.data.model.NoteColor? = null,
): Occurrence {
    find(reminderId, scheduledAt)?.let { return it }
    val new = Occurrence(
        reminderId = reminderId,
        scheduledAt = scheduledAt,
        status = status,
        title = title,
        color = color,
    )
    val id = insert(new)
    return if (id >= 0) new.copy(id = id) else find(reminderId, scheduledAt) ?: new
}
