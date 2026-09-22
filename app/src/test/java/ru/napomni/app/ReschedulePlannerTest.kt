package ru.napomni.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.napomni.app.data.model.Reminder
import ru.napomni.app.data.model.ScheduleType
import ru.napomni.app.domain.ReschedulePlanner
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Юнит-тесты перепланирования и поиска пропущенных (ТЗ, FR-9, п.9). */
class ReschedulePlannerTest {

    /** Фиксированный пояс — тесты не зависят от настроек машины. */
    private val zone: ZoneId = ZoneId.of("UTC")

    /** «Сейчас»: 22.09.2026 12:00 UTC. */
    private val now: LocalDateTime = LocalDateTime.of(2026, 9, 22, 12, 0)

    /** Напоминание создано 01.09.2026 05:00 UTC, ежедневно в 9:00 и 15:00 с 01.09. */
    private val reminder: Reminder = Reminder(
        title = "Таблетки",
        scheduleType = ScheduleType.DAILY,
        times = listOf(LocalTime.of(9, 0), LocalTime.of(15, 0)),
        startDate = LocalDate.of(2026, 9, 1),
        createdAt = Instant.parse("2026-09-01T05:00:00Z"),
        updatedAt = Instant.parse("2026-09-01T05:00:00Z"),
    )

    @Test
    fun allMissedSlotsInLookbackWindow() {
        // Окно поиска: 19.09 12:00 → 22.09 12:00 (3 дня).
        val slots = ReschedulePlanner.missedSlots(reminder, now, zone)
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 9, 19, 15, 0),
                LocalDateTime.of(2026, 9, 20, 9, 0),
                LocalDateTime.of(2026, 9, 20, 15, 0),
                LocalDateTime.of(2026, 9, 21, 9, 0),
                LocalDateTime.of(2026, 9, 21, 15, 0),
                LocalDateTime.of(2026, 9, 22, 9, 0),
            ),
            slots,
        )
    }

    @Test
    fun respectsGracePeriod() {
        // Срабатывание 2 минуты назад — не трогаем (его мог обработать приёмник).
        val fresh = reminder.copy(
            times = listOf(LocalTime.of(11, 58)),
            startDate = LocalDate.of(2026, 9, 22),
            createdAt = Instant.parse("2026-09-22T05:00:00Z"),
        )
        val slots = ReschedulePlanner.missedSlots(fresh, now, zone)
        // 11:58 младше льготы 5 минут (cutoff = 11:55) — пусто.
        assertTrue(slots.isEmpty())
    }

    @Test
    fun looksBackThreeDaysOnly() {
        val slots = ReschedulePlanner.missedSlots(reminder, now, zone)
        // Ничего старше 19.09 12:00.
        assertTrue(slots.none { it.isBefore(LocalDateTime.of(2026, 9, 19, 12, 0)) })
        // И ровно до сейчас (сегодняшние 9:00 — крайнее).
        assertEquals(LocalDateTime.of(2026, 9, 22, 9, 0), slots.max())
    }

    @Test
    fun skipsSlotsBeforeCreation() {
        // Создано в 09:30 — сегодняшние 9:00 «до создания» не считаются (FR-2.2).
        val fresh = reminder.copy(createdAt = Instant.parse("2026-09-22T09:30:00Z"))
        val slots = ReschedulePlanner.missedSlots(fresh, now, zone)
        assertTrue(slots.isEmpty())
    }

    @Test
    fun disabledReminderYieldsNothing() {
        val slots = ReschedulePlanner.missedSlots(reminder.copy(enabled = false), now, zone)
        assertTrue(slots.isEmpty())
    }

    @Test
    fun noFutureSlots() {
        val slots = ReschedulePlanner.missedSlots(reminder, now, zone)
        assertTrue(slots.isNotEmpty())
        assertTrue(slots.all { it.isBefore(now.minusMinutes(ReschedulePlanner.GRACE_MINUTES)) })
    }

    @Test
    fun maxOccurrencesStillRespected() {
        // «Курс» из 2 срабатываний: 01.09 9:00 и 15:00 — дальше пусто даже в окне.
        val course = reminder.copy(maxOccurrences = 2)
        val slots = ReschedulePlanner.missedSlots(course, now, zone)
        assertTrue(slots.isEmpty())
    }
}
