package ru.napomni.app.domain

import ru.napomni.app.data.model.Reminder
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Чистая логика перепланирования (ТЗ, FR-9, п.9 граничных случаев).
 *
 * Когда телефон был выключен/разряжен, срабатывания не сработали и записей о них нет.
 * При старте/перезагрузке такие «минувшие» срабатывания помечаются «пропущено» —
 * без уведомлений («не спамим»), как согласовано в ТЗ.
 */
object ReschedulePlanner {

    /** Льгота: срабатывания младше N минут не трогаем (их мог обработать приёмник). */
    const val GRACE_MINUTES = 5L

    /** Сколько дней назад ищем пропущенные срабатывания. */
    const val LOOKBACK_DAYS = 3L

    /**
     * Срабатывания в прошлом, которые нужно пометить «пропущено»:
     *  - после создания напоминания (FR-2.2: прошедшие при создании не считаются);
     *  - строго старше льготы;
     *  - не старше окна поиска;
     *  - только для включённых напоминаний.
     * Не учитывает уже существующие записи — вызывающий проверяет БД.
     * [zone] — часовой пояс устройства (в тестах передаётся явно).
     */
    fun missedSlots(
        reminder: Reminder,
        now: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<LocalDateTime> {
        if (!reminder.enabled) return emptyList()
        val created = LocalDateTime.ofInstant(reminder.createdAt, zone)
        val cutoff = now.minusMinutes(GRACE_MINUTES)
        val windowStart = maxOf(
            now.minusDays(LOOKBACK_DAYS),
            reminder.startDate.atStartOfDay(),
            created,
        )
        if (!windowStart.isBefore(cutoff)) return emptyList()

        val slots = mutableListOf<LocalDateTime>()
        var date = windowStart.toLocalDate()
        val lastDate = now.toLocalDate()
        while (!date.isAfter(lastDate)) {
            ScheduleCalculator.occurrencesOn(reminder, date)
                .filter {
                    it.isAfter(created) &&
                        !it.isBefore(windowStart) &&
                        it.isBefore(cutoff)
                }
                .forEach { slots += it }
            date = date.plusDays(1)
        }
        return slots
    }
}
