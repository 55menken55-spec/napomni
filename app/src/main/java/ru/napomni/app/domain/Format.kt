package ru.napomni.app.domain

import ru.napomni.app.data.model.Reminder
import ru.napomni.app.data.model.ScheduleType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

fun formatTime(time: LocalTime): String = time.format(timeFormat)

fun formatDate(date: LocalDate): String = date.format(dateFormat)

fun weekdayShort(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Пн"
    DayOfWeek.TUESDAY -> "Вт"
    DayOfWeek.WEDNESDAY -> "Ср"
    DayOfWeek.THURSDAY -> "Чт"
    DayOfWeek.FRIDAY -> "Пт"
    DayOfWeek.SATURDAY -> "Сб"
    DayOfWeek.SUNDAY -> "Вс"
}

/** Человекочитаемое расписание: «Каждый день, 9:00, 15:00» и т.п. */
fun describeSchedule(reminder: Reminder): String {
    val times = reminder.times.sorted().joinToString(", ") { formatTime(it) }
    val base = when (reminder.scheduleType) {
        ScheduleType.ONCE -> "Раз, ${formatDate(reminder.startDate)}, $times"
        ScheduleType.DAILY -> "Каждый день, $times"
        ScheduleType.WEEKLY -> {
            val days = reminder.weekdays.sortedBy { it.value }.joinToString(", ") { weekdayShort(it) }
            "$days, $times"
        }
        ScheduleType.INTERVAL_DAYS -> "Каждые ${reminder.intervalDays ?: "?"} дн., $times"
    }
    val end = when {
        reminder.endDate != null -> " · до ${formatDate(reminder.endDate)}"
        reminder.maxOccurrences != null -> " · ${reminder.maxOccurrences} раз"
        else -> ""
    }
    return base + end
}

/** «сегодня 15:00» / «завтра 09:00» / «22.09 15:00». */
fun describeNext(next: LocalDateTime?, now: LocalDateTime): String {
    if (next == null) return "нет срабатываний"
    val time = formatTime(next.toLocalTime())
    val today = now.toLocalDate()
    return when (next.toLocalDate()) {
        today -> "сегодня $time"
        today.plusDays(1) -> "завтра $time"
        else -> "${formatDate(next.toLocalDate())} $time"
    }
}
