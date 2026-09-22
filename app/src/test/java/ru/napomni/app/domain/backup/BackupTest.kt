package ru.napomni.app.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class BackupTest {

    private val reminder = Reminder(
        id = 7, title = "Таблетки", details = "после еды",
        scheduleType = ScheduleType.WEEKLY,
        times = listOf(LocalTime.of(9, 0), LocalTime.of(15, 0)),
        weekdays = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        intervalDays = null,
        startDate = LocalDate.of(2026, 9, 1),
        endDate = LocalDate.of(2026, 12, 31),
        maxOccurrences = 20,
        importance = ReminderImportance.ALARM,
        categoryId = 3,
        color = NoteColor.ORANGE,
        enabled = true,
        createdAt = Instant.parse("2026-09-01T08:00:00Z"),
        updatedAt = Instant.parse("2026-09-02T08:00:00Z"),
    )

    private val note = Note(
        id = 5, title = "Продукты", color = NoteColor.YELLOW,
        categoryId = null, pinned = true, archived = false,
        createdAt = Instant.parse("2026-09-02T08:00:00Z"),
        updatedAt = Instant.parse("2026-09-03T08:00:00Z"),
    )

    private val block = NoteBlock(
        id = 9, noteId = 5, type = NoteBlockType.CHECK, text = "Хлеб",
        done = true, orderIndex = 0,
    )

    private val category = Category(id = 3, name = "Здоровье", color = NoteColor.ORANGE)

    private val occurrence = Occurrence(
        id = 11, reminderId = 7,
        scheduledAt = Instant.parse("2026-09-22T12:00:00Z"),
        status = OccurrenceStatus.DONE,
        title = "Таблетки", color = NoteColor.ORANGE,
        completedAt = Instant.parse("2026-09-22T12:03:00Z"),
        snoozeCount = 1,
        snoozedUntil = Instant.parse("2026-09-22T12:15:00Z"),
    )

    private fun sampleFile(): BackupFile = with(BackupMapper) {
        BackupFile(
            schemaVersion = BACKUP_SCHEMA_VERSION,
            exportedAt = 1_758_535_200_000L,
            reminders = listOf(reminder.toDto()),
            notes = listOf(note.toDto()),
            blocks = listOf(block.toDto()),
            categories = listOf(category.toDto()),
            occurrences = listOf(occurrence.toDto()),
            settings = SettingsDto(
                themeMode = "DARK",
                snoozePresets = listOf(10, 20),
                soundNotificationsUri = "content://media/external/audio/media/1",
                soundAlarmUri = null,
            ),
        )
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val original = sampleFile()
        val json = BackupMapper.encode(original)
        assertTrue("json содержит версию схемы", json.contains("schemaVersion"))
        val restored = BackupMapper.decode(json)
        assertEquals(original, restored)
    }

    @Test
    fun entityDtoRoundTrip() {
        with(BackupMapper) {
            assertEquals(reminder, reminder.toDto().toEntity())
            assertEquals(note, note.toDto().toEntity())
            assertEquals(block, block.toDto().toEntity())
            assertEquals(category, category.toDto().toEntity())
            assertEquals(occurrence, occurrence.toDto().toEntity())
        }
    }

    @Test
    fun futureSchemaIsRejected() {
        val json = BackupMapper.encode(sampleFile())
            .replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")
        try {
            BackupMapper.decode(json)
            throw AssertionError("ожидалась ошибка при schemaVersion=2")
        } catch (e: IllegalArgumentException) {
            // ожидаемо
        }
    }

    @Test
    fun unknownKeysAreIgnored() {
        val encoded = BackupMapper.encode(sampleFile())
        val json = encoded.substring(0, 1) + "\"futureField\":123," + encoded.substring(1)
        val restored = BackupMapper.decode(json)
        assertEquals(sampleFile().reminders, restored.reminders)
    }
}
