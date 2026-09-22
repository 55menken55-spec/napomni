package ru.napomni.app.domain

import ru.napomni.app.data.alarm.AlarmScheduler
import ru.napomni.app.data.db.NapomniDatabase
import ru.napomni.app.data.db.getOrCreate
import ru.napomni.app.data.model.Occurrence
import ru.napomni.app.data.model.OccurrenceStatus
import ru.napomni.app.data.model.Reminder
import ru.napomni.app.data.model.ReminderImportance
import ru.napomni.app.data.notify.NotificationHelper
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Общая логика срабатываний (ТЗ, FR-4, FR-5).
 * Используется из приёмников (AlarmReceiver, NotificationActionReceiver) и экрана срабатывания.
 */
class OccurrenceActions(
    private val database: NapomniDatabase,
    private val scheduler: AlarmScheduler,
    private val notifications: NotificationHelper,
) {

    /** Пресеты отложения (подключаются из SettingsRepository при создании контейнера). */
    var settingsPresets: suspend () -> List<Int> = { listOf(5, 15, 30) }

    /**
     * Срабатывание будильника: создаёт/находит запись истории, показывает уведомление
     * и планирует следующее срабатывание + проверку «пропущено».
     * @return напоминание, если нужно поверх показать экран «будильник» (FR-3.2).
     */
    suspend fun onAlarmFired(
        reminderId: Long,
        triggerAtMillis: Long,
        kind: String,
        wantActivity: Boolean,
    ): Reminder? {
        val reminder = database.reminderDao().getById(reminderId) ?: return null
        if (!reminder.enabled) return null

        val dao = database.occurrenceDao()
        val scheduledAt = Instant.ofEpochMilli(triggerAtMillis)
        val occurrence = dao.getOrCreate(
            reminderId = reminderId,
            scheduledAt = scheduledAt,
            status = OccurrenceStatus.PENDING,
            title = reminder.title,
            color = reminder.color,
        )

        // Регулярное срабатывание двигает расписание вперёд (следующее по расписанию).
        if (kind == AlarmScheduler.KIND_REGULAR) {
            scheduler.scheduleNext(reminder)
        }

        // Проверка «пропущено» через окно (FR-5.3).
        val alarmMode = reminder.importance == ReminderImportance.ALARM
        val missedAt = LocalDateTime.now().plusMinutes(Limits.missedWindowMin(alarmMode))
        scheduler.scheduleMissedCheck(reminder, occurrence.id, missedAt)

        notifications.notify(
            occurrence.id,
            notifications.buildReminderNotification(
                reminder = reminder,
                occurrenceId = occurrence.id,
                snoozePresets = settingsPresets(),
                alarmMode = alarmMode,
            ),
        )

        return if (wantActivity && alarmMode) reminder else null
    }

    /** «Выполнено» (FR-4.6). */
    suspend fun complete(reminderId: Long, occurrenceId: Long) {
        close(reminderId, occurrenceId, OccurrenceStatus.DONE)
    }

    /** «Пропустить» (FR-5). */
    suspend fun skip(reminderId: Long, occurrenceId: Long) {
        close(reminderId, occurrenceId, OccurrenceStatus.SKIPPED)
    }

    /** «Отложить на N минут» — только текущее срабатывание (FR-4.4). */
    suspend fun snooze(reminderId: Long, occurrenceId: Long, minutes: Int) {
        if (minutes !in 1..180) return
        val reminder = database.reminderDao().getById(reminderId) ?: return
        val occurrence = database.occurrenceDao().getById(occurrenceId) ?: return

        val until = LocalDateTime.now().plusMinutes(minutes.toLong())
        database.occurrenceDao().update(
            occurrence.copy(
                status = OccurrenceStatus.SNOOZED,
                snoozeCount = occurrence.snoozeCount + 1,
                snoozedUntil = until.atZone(ZoneId.systemDefault()).toInstant(),
            ),
        )
        notifications.cancel(occurrenceId)
        scheduler.cancelMissed(occurrenceId)
        scheduler.scheduleSnooze(reminder, occurrenceId, until)
    }

    /** Проверка окна: незакрытое срабатывание помечается «пропущено» (FR-5.3). */
    suspend fun markMissedIfStillPending(reminderId: Long, occurrenceId: Long) {
        val occurrence = database.occurrenceDao().getById(occurrenceId) ?: return
        if (occurrence.status != OccurrenceStatus.PENDING) return
        database.occurrenceDao().updateStatus(occurrenceId, OccurrenceStatus.MISSED, null)
        notifications.cancel(occurrenceId)
        scheduler.cancelMissed(occurrenceId)
    }

    private suspend fun close(reminderId: Long, occurrenceId: Long, status: OccurrenceStatus) {
        database.occurrenceDao().updateStatus(occurrenceId, status, Instant.now())
        notifications.cancel(occurrenceId)
        scheduler.cancelMissed(occurrenceId)
        scheduler.cancelSnooze(occurrenceId)
    }
}
