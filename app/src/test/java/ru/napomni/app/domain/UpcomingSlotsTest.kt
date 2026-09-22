package ru.napomni.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.napomni.app.data.model.NoteColor
import ru.napomni.app.data.model.Reminder
import ru.napomni.app.data.model.ReminderImportance
import ru.napomni.app.data.model.ScheduleType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class UpcomingSlotsTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 22) // вторник

    private fun reminder(
        id: Long,
        time: LocalTime,
        type: ScheduleType = ScheduleType.DAILY,
        firstDate: LocalDate = today,
        enabled: Boolean = true,
    ) = Reminder(
        id = id, title = "Дело $id",
        scheduleType = type,
        times = listOf(time),
        weekdays = emptyList(),
        intervalDays = null,
        startDate = firstDate,
        endDate = null,
        maxOccurrences = null,
        importance = ReminderImportance.NOTIFICATION,
        categoryId = null,
        color = NoteColor.BLUE,
        enabled = enabled,
        createdAt = Instant.parse("2026-09-01T08:00:00Z"),
        updatedAt = Instant.parse("2026-09-01T08:00:00Z"),
    )

    @Test
    fun `sorted globally and limited`() {
        val now = today.atTime(6, 0)
        val reminders = listOf(
            reminder(1, LocalTime.of(15, 0)),
            reminder(2, LocalTime.of(8, 0)),
            reminder(3, LocalTime.of(12, 0)),
        )
        val slots = UpcomingSlots.upcoming(reminders, now, limit = 4)
        assertEquals(3, slots.size)
        assertEquals("Дело 2", slots[0].title)
        assertEquals("Дело 3", slots[1].title)
        assertEquals("Дело 1", slots[2].title)
    }

    @Test
    fun `today already-passed time rolls to next occurrence`() {
        val now = today.atTime(16, 0)
        val reminders = listOf(reminder(1, LocalTime.of(15, 0)))
        val slots = UpcomingSlots.upcoming(reminders, now)
        assertEquals(today.plusDays(1), slots[0].at.toLocalDate())
    }

    @Test
    fun `disabled reminders are skipped`() {
        val now = today.atTime(9, 0)
        val reminders = listOf(
            reminder(1, LocalTime.of(10, 0), enabled = false),
            reminder(2, LocalTime.of(11, 0)),
        )
        val slots = UpcomingSlots.upcoming(reminders, now)
        assertEquals(1, slots.size)
        assertEquals("Дело 2", slots[0].title)
    }

    @Test
    fun `once in the past is not shown`() {
        val now = today.atTime(20, 0)
        val reminders = listOf(
            reminder(1, LocalTime.of(15, 0), type = ScheduleType.ONCE, firstDate = today),
            reminder(2, LocalTime.of(10, 0), type = ScheduleType.ONCE, firstDate = today.plusDays(1)),
        )
        val slots = UpcomingSlots.upcoming(reminders, now)
        assertEquals(1, slots.size)
        assertEquals("Дело 2", slots[0].title)
    }

    @Test
    fun `limit takes nearest slots only`() {
        val now = today.atTime(6, 0)
        val reminders = (1L..6L).map { reminder(it, LocalTime.of(6 + it.toInt(), 0)) }
        val slots = UpcomingSlots.upcoming(reminders, now, limit = 4)
        assertEquals(4, slots.size)
        assertEquals("Дело 1", slots.first().title)
        assertEquals("Дело 4", slots.last().title)
    }
}
