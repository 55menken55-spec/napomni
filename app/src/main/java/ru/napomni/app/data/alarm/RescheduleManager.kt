package ru.napomni.app.data.alarm

import kotlinx.coroutines.flow.first
import ru.napomni.app.data.db.NapomniDatabase
import ru.napomni.app.data.db.getOrCreate
import ru.napomni.app.data.model.OccurrenceStatus
import ru.napomni.app.data.repository.ReminderRepository
import ru.napomni.app.domain.ReschedulePlanner
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Перепланирование всех будильников (ТЗ, FR-9).
 * Вызывается: при старте приложения, после перезагрузки, смены времени/часового пояса
 * и ежедневной страховкой WorkManager (FR-9.3).
 */
class RescheduleManager(
    private val database: NapomniDatabase,
    private val reminderRepository: ReminderRepository,
    private val alarmScheduler: AlarmScheduler,
) {

    suspend fun rescheduleAll() {
        val reminders = reminderRepository.observeAll().first()
        val now = LocalDateTime.now()
        for (reminder in reminders) {
            if (!reminder.enabled) continue
            markMissedWhileOff(reminder, now)
            alarmScheduler.scheduleNext(reminder)
        }
    }

    /** Помечает «пропущено» срабатывания, не случившиеся из-за выключенного телефона (п.9). */
    private suspend fun markMissedWhileOff(
        reminder: ru.napomni.app.data.model.Reminder,
        now: LocalDateTime,
    ) {
        val dao = database.occurrenceDao()
        ReschedulePlanner.missedSlots(reminder, now).forEach { slot ->
            dao.getOrCreate(
                reminderId = reminder.id,
                scheduledAt = slot.atZone(ZoneId.systemDefault()).toInstant(),
                status = OccurrenceStatus.MISSED,
                title = reminder.title,
                color = reminder.color,
            )
        }
    }
}
