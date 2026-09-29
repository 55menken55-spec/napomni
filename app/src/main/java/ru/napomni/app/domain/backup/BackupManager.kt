package ru.napomni.app.domain.backup

import androidx.room.withTransaction
import ru.napomni.app.data.alarm.AlarmScheduler
import ru.napomni.app.data.alarm.RescheduleManager
import ru.napomni.app.data.db.NapomniDatabase
import ru.napomni.app.data.settings.SettingsRepository
import ru.napomni.app.data.model.ThemeMode
import kotlinx.coroutines.flow.first

/**
 * Экспорт/импорт резервной копии (ТЗ, FR-10).
 * Импорт — полная замена текущих данных (FR-10.3).
 */
class BackupManager(
    private val database: NapomniDatabase,
    private val settings: SettingsRepository,
    private val alarmScheduler: AlarmScheduler,
    private val rescheduleManager: RescheduleManager,
) {

    suspend fun exportToJson(): String = with(BackupMapper) {
        val file = BackupFile(
            schemaVersion = BACKUP_SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            reminders = database.reminderDao().getAll().map { it.toDto() },
            notes = database.noteDao().getAllNotes().map { it.toDto() },
            blocks = database.noteDao().getAllBlocks().map { it.toDto() },
            categories = database.categoryDao().getAll().map { it.toDto() },
            occurrences = database.occurrenceDao().getAll().map { it.toDto() },
            settings = SettingsDto(
                themeMode = settings.themeMode.first().name,
                snoozePresets = settings.snoozePresets.first(),
                soundNotificationsUri = settings.soundNotificationsUri.first(),
                soundAlarmUri = settings.soundAlarmUri.first(),
            ),
        )
        encode(file)
    }

    /** Полная замена данных из файла. Бросает исключение при неверном формате. */
    suspend fun importFromJson(text: String) {
        val file = with(BackupMapper) { decode(text) }
        require(file.schemaVersion <= BACKUP_SCHEMA_VERSION) {
            "Неподдерживаемая версия файла: ${file.schemaVersion}"
        }
        // Снимаем будильники прежних напоминаний: их записи сейчас будут заменены.
        database.reminderDao().getAll().forEach { alarmScheduler.cancelReminder(it.id) }
        with(BackupMapper) {
            database.withTransaction {
                // Полная замена (FR-10.3).
                database.noteDao().clearAllBlocks()
                database.noteDao().clearAllNotes()
                database.occurrenceDao().clearAll()
                database.categoryDao().clearAll()
                database.reminderDao().clearAll()

                database.reminderDao().insertAll(file.reminders.map { it.toEntity() })
                database.noteDao().insertAllNotes(file.notes.map { it.toEntity() })
                database.noteDao().insertAllBlocks(file.blocks.map { it.toEntity() })
                database.categoryDao().insertAll(file.categories.map { it.toEntity() })
                database.occurrenceDao().insertAll(file.occurrences.map { it.toEntity() })
            }
            settings.setThemeMode(
                runCatching { ThemeMode.valueOf(file.settings.themeMode) }
                    .getOrDefault(ThemeMode.SYSTEM),
            )
            settings.setSnoozePresets(file.settings.snoozePresets)
            settings.setSoundNotifications(file.settings.soundNotificationsUri)
            settings.setSoundAlarm(file.settings.soundAlarmUri)
        }
        // Будильники восстановленных напоминаний. Без этого уведомления не приходили бы
        // до следующего запуска приложения / перезагрузки (баг v1.1).
        rescheduleManager.rescheduleAll()
    }
}
