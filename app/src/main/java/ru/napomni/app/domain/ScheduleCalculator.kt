package ru.napomni.app.domain

import ru.napomni.app.data.model.Reminder
import ru.napomni.app.data.model.ScheduleType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Расчёт срабатываний по расписанию напоминания (ТЗ, FR-2).
 * Всё считается по «настенному» локальному времени (FR-2.3).
 */
object ScheduleCalculator {

    /** Все срабатывания напоминания на дату (с учётом окончания и maxOccurrences). */
    fun occurrencesOn(reminder: Reminder, date: LocalDate): List<LocalDateTime> {
        if (!dayMatches(reminder, date)) return emptyList()
        val slots: List<LocalTime> =
            (if (reminder.scheduleType == ScheduleType.ONCE) listOfNotNull(reminder.times.firstOrNull())
            else reminder.times).sorted()
        if (slots.isEmpty()) return emptyList()

        val plannedBefore = plannedCountBefore(reminder, date)
        val max = reminder.maxOccurrences
        return slots.mapIndexedNotNull { index, time ->
            if (max == null || plannedBefore + index < max) date.atTime(time) else null
        }
    }

    /** Ближайшее срабатывание строго после указанного момента (или null). */
    fun nextOccurrenceAfter(reminder: Reminder, after: LocalDateTime): LocalDateTime? {
        if (!reminder.enabled) return null
        var date = after.toLocalDate()
        val limit = date.plusYears(5)
        while (!date.isAfter(limit)) {
            occurrencesOn(reminder, date).firstOrNull { it.isAfter(after) }?.let { return it }
            date = date.plusDays(1)
        }
        return null
    }

    /** Есть ли у напоминания будущие срабатывания (учитывая окончание). */
    fun hasFutureOccurrences(reminder: Reminder): Boolean =
        nextOccurrenceAfter(reminder, LocalDateTime.now()) != null

    private fun dayMatches(r: Reminder, date: LocalDate): Boolean {
        if (date.isBefore(r.startDate)) return false
        r.endDate?.let { end -> if (date.isAfter(end)) return false }
        return when (r.scheduleType) {
            ScheduleType.ONCE -> date == r.startDate
            ScheduleType.DAILY -> true
            ScheduleType.WEEKLY -> r.weekdays.contains(date.dayOfWeek)
            ScheduleType.INTERVAL_DAYS -> {
                val n = r.intervalDays ?: return false
                n > 0 && ChronoUnit.DAYS.between(r.startDate, date) % n == 0L
            }
        }
    }

    /** Число срабатываний строго до указанной даты (для учёта maxOccurrences). */
    private fun plannedCountBefore(r: Reminder, date: LocalDate): Long {
        var count = 0L
        var day = r.startDate
        var guard = 0
        while (day.isBefore(date) && guard < 20_000) {
            if (dayMatches(r, day)) {
                count += if (r.scheduleType == ScheduleType.ONCE) 1 else r.times.size
            }
            day = day.plusDays(1)
            guard++
        }
        return count
    }
}
