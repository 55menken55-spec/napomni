package ru.napomni.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

// ---------- Перечисления ----------

/** Тип расписания напоминания (ТЗ, FR-2). */
enum class ScheduleType { ONCE, DAILY, WEEKLY, INTERVAL_DAYS }

/** Важность: обычное уведомление или «как будильник» (ТЗ, FR-3). */
enum class ReminderImportance { NOTIFICATION, ALARM }

/** Статус срабатывания (ТЗ, FR-5). */
enum class OccurrenceStatus { PENDING, SNOOZED, DONE, MISSED, SKIPPED }

/** Блок заметки: текст или пункт чек-листа (ТЗ, FR-7). */
enum class NoteBlockType { TEXT, CHECK }

/** Палитра из 8 цветов (ТЗ, FR-1.1 / FR-7.1). */
enum class NoteColor { YELLOW, GREEN, BLUE, PURPLE, PINK, ORANGE, TEAL, GREY }

/** Тема оформления. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Режим окончания расписания (ТЗ, FR-2.5). */
enum class EndMode { NEVER, UNTIL_DATE, AFTER_COUNT }

// ---------- Сущности ----------

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val details: String = "",
    val scheduleType: ScheduleType,
    /** Времена суток срабатывания (1–6 штук). Для ONCE — ровно одно. */
    val times: List<LocalTime> = emptyList(),
    /** Дни недели (для WEEKLY). */
    val weekdays: List<DayOfWeek> = emptyList(),
    /** Интервал в днях (для INTERVAL_DAYS). */
    val intervalDays: Int? = null,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    /** Окончание «после K срабатываний» (ТЗ, FR-2.5). */
    val maxOccurrences: Int? = null,
    val importance: ReminderImportance = ReminderImportance.NOTIFICATION,
    val categoryId: Long? = null,
    val color: NoteColor = NoteColor.BLUE,
    val enabled: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val color: NoteColor = NoteColor.YELLOW,
    val categoryId: Long? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "note_blocks",
    indices = [Index("noteId")],
)
data class NoteBlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val type: NoteBlockType,
    val text: String = "",
    val done: Boolean = false,
    val orderIndex: Int,
)

data class NoteWithBlocks(
    @androidx.room.Embedded val note: Note,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "noteId")
    val blocks: List<NoteBlock>,
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: NoteColor = NoteColor.GREY,
)

@Entity(
    tableName = "occurrences",
    // Уникальность защищает от дублей при гонке «перепланировщиков» (ТЗ, FR-9.3).
    indices = [Index(value = ["reminderId", "scheduledAt"], unique = true), Index("scheduledAt")],
)
data class Occurrence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ID напоминания (без FK: история сохраняется и после удаления напоминания, ТЗ FR-6.1). */
    val reminderId: Long,
    val scheduledAt: Instant,
    val status: OccurrenceStatus = OccurrenceStatus.PENDING,
    /** Снимок названия/цвета на момент срабатывания — для истории удалённых напоминаний. */
    val title: String = "",
    val color: NoteColor? = null,
    val completedAt: Instant? = null,
    val snoozeCount: Int = 0,
    val snoozedUntil: Instant? = null,
)
