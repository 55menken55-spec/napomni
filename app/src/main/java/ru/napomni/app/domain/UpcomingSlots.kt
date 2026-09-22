package ru.napomni.app.domain

import ru.napomni.app.data.model.Reminder

/**
 * Ближайшие срабатывания по всем напоминаниям для виджета (ТЗ: FR-11).
 * Чистая функция: глобально ближайшие слоты, [limit] штук.
 */
object UpcomingSlots {

    data class Slot(
        val reminderId: Long,
        val title: String,
        val at: java.time.LocalDateTime,
    )

    fun upcoming(
        reminders: List<Reminder>,
        now: java.time.LocalDateTime = java.time.LocalDateTime.now(),
        limit: Int = 4,
    ): List<Slot> {
        return reminders
            .filter { it.enabled }
            .mapNotNull { reminder ->
                ScheduleCalculator.nextOccurrenceAfter(reminder, now)?.let { at ->
                    Slot(reminder.id, reminder.title, at)
                }
            }
            .sortedBy { it.at }
            .take(limit)
    }
}
