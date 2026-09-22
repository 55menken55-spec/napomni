package ru.napomni.app.domain.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.napomni.app.data.model.Category
import ru.napomni.app.data.model.Note
import ru.napomni.app.data.model.NoteBlock
import ru.napomni.app.data.model.NoteBlockType
import ru.napomni.app.data.model.NoteColor
import ru.napomni.app.data.model.Occurrence
import ru.napomni.app.data.model.OccurrenceStatus
import ru.napomni.app.data.model.Reminder
import ru.napomni.app.data.model.ReminderImportance
import ru.napomni.app.data.model.ScheduleType
import ru.napomni.app.data.model.ThemeMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Формат резервной копии (ТЗ, FR-10): один JSON-файл со всеми данными.
 * Сериализация — kotlinx.serialization; маппинг DTO ⇄ сущности чистый и тестируемый.
 */

const val BACKUP_SCHEMA_VERSION = 1

@Serializable
data class BackupFile(
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val exportedAt: Long = 0L,
    val reminders: List<ReminderDto> = emptyList(),
    val notes: List<NoteDto> = emptyList(),
    val blocks: List<BlockDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList(),
    val occurrences: List<OccurrenceDto> = emptyList(),
    val settings: SettingsDto = SettingsDto(),
)

@Serializable
data class ReminderDto(
    val id: Long = 0,
    val title: String = "",
    val details: String = "",
    val scheduleType: String = "DAILY",
    val times: List<String> = emptyList(),
    val weekdays: List<Int> = emptyList(),
    val intervalDays: Int? = null,
    val startDate: String = "",
    val endDate: String? = null,
    val maxOccurrences: Int? = null,
    val importance: String = "NOTIFICATION",
    val categoryId: Long? = null,
    val color: String = "BLUE",
    val enabled: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class NoteDto(
    val id: Long = 0,
    val title: String = "",
    val color: String = "YELLOW",
    val categoryId: Long? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class BlockDto(
    val id: Long = 0,
    val noteId: Long = 0,
    val type: String = "TEXT",
    val text: String = "",
    val done: Boolean = false,
    val orderIndex: Int = 0,
)

@Serializable
data class CategoryDto(
    val id: Long = 0,
    val name: String = "",
    val color: String = "GREY",
)

@Serializable
data class OccurrenceDto(
    val id: Long = 0,
    val reminderId: Long = 0,
    val scheduledAt: Long = 0L,
    val status: String = "PENDING",
    val title: String = "",
    val color: String? = null,
    val completedAt: Long? = null,
    val snoozeCount: Int = 0,
    val snoozedUntil: Long? = null,
)

@Serializable
data class SettingsDto(
    val themeMode: String = "SYSTEM",
    val snoozePresets: List<Int> = listOf(5, 15, 30),
    val soundNotificationsUri: String? = null,
    val soundAlarmUri: String? = null,
)

object BackupMapper {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        // Файл должен быть полным и самодостаточным — пишем и значения по умолчанию.
        encodeDefaults = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(file)

    fun decode(text: String): BackupFile = json.decodeFromString<BackupFile>(text).also { file ->
        require(file.schemaVersion <= BACKUP_SCHEMA_VERSION) {
            "Неподдерживаемая версия резервной копии: ${file.schemaVersion}"
        }
    }

    fun Reminder.toDto() = ReminderDto(
        id = id,
        title = title,
        details = details,
        scheduleType = scheduleType.name,
        times = times.map { it.toString() },
        weekdays = weekdays.map { it.value },
        intervalDays = intervalDays,
        startDate = startDate.toString(),
        endDate = endDate?.toString(),
        maxOccurrences = maxOccurrences,
        importance = importance.name,
        categoryId = categoryId,
        color = color.name,
        enabled = enabled,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
    )

    fun ReminderDto.toEntity() = Reminder(
        id = id,
        title = title,
        details = details,
        scheduleType = ScheduleType.valueOf(scheduleType),
        times = times.map { LocalTime.parse(it) },
        weekdays = weekdays.map { java.time.DayOfWeek.of(it) },
        intervalDays = intervalDays,
        startDate = LocalDate.parse(startDate),
        endDate = endDate?.let { LocalDate.parse(it) },
        maxOccurrences = maxOccurrences,
        importance = ReminderImportance.valueOf(importance),
        categoryId = categoryId,
        color = runCatching { NoteColor.valueOf(color) }.getOrDefault(NoteColor.BLUE),
        enabled = enabled,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )

    fun Note.toDto() = NoteDto(
        id = id,
        title = title,
        color = color.name,
        categoryId = categoryId,
        pinned = pinned,
        archived = archived,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
    )

    fun NoteDto.toEntity() = Note(
        id = id,
        title = title,
        color = runCatching { NoteColor.valueOf(color) }.getOrDefault(NoteColor.YELLOW),
        categoryId = categoryId,
        pinned = pinned,
        archived = archived,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )

    fun NoteBlock.toDto() = BlockDto(
        id = id,
        noteId = noteId,
        type = type.name,
        text = text,
        done = done,
        orderIndex = orderIndex,
    )

    fun BlockDto.toEntity() = NoteBlock(
        id = id,
        noteId = noteId,
        type = runCatching { NoteBlockType.valueOf(type) }.getOrDefault(NoteBlockType.TEXT),
        text = text,
        done = done,
        orderIndex = orderIndex,
    )

    fun Category.toDto() = CategoryDto(id = id, name = name, color = color.name)

    fun CategoryDto.toEntity() = Category(
        id = id,
        name = name,
        color = runCatching { NoteColor.valueOf(color) }.getOrDefault(NoteColor.GREY),
    )

    fun Occurrence.toDto() = OccurrenceDto(
        id = id,
        reminderId = reminderId,
        scheduledAt = scheduledAt.toEpochMilli(),
        status = status.name,
        title = title,
        color = color?.name,
        completedAt = completedAt?.toEpochMilli(),
        snoozeCount = snoozeCount,
        snoozedUntil = snoozedUntil?.toEpochMilli(),
    )

    fun OccurrenceDto.toEntity() = Occurrence(
        id = id,
        reminderId = reminderId,
        scheduledAt = Instant.ofEpochMilli(scheduledAt),
        status = runCatching { OccurrenceStatus.valueOf(status) }
            .getOrDefault(OccurrenceStatus.PENDING),
        title = title,
        color = color?.let { runCatching { NoteColor.valueOf(it) }.getOrNull() },
        completedAt = completedAt?.let(Instant::ofEpochMilli),
        snoozeCount = snoozeCount,
        snoozedUntil = snoozedUntil?.let(Instant::ofEpochMilli),
    )
}
