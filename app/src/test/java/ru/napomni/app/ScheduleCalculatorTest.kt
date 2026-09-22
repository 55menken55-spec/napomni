package ru.napomni.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.napomni.app.data.model.Reminder
import ru.napomni.app.data.model.ScheduleType
import ru.napomni.app.domain.ScheduleCalculator
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Юнит-тесты расчёта расписаний (ТЗ, FR-2). */
class ScheduleCalculatorTest {

    private val start: LocalDate = LocalDate.of(2026, 9, 1)
    private val nowInstant: Instant = Instant.parse("2026-09-22T10:00:00Z")

    private fun reminder(
        type: ScheduleType,
        times: List<LocalTime> = listOf(LocalTime.of(15, 0)),
        weekdays: List<DayOfWeek> = emptyList(),
        intervalDays: Int? = null,
        startDate: LocalDate = start,
        endDate: LocalDate? = null,
        maxOccurrences: Int? = null,
    ) = Reminder(
        title = "Тест",
        scheduleType = type,
        times = times,
        weekdays = weekdays,
        intervalDays = intervalDays,
        startDate = startDate,
        endDate = endDate,
        maxOccurrences = maxOccurrences,
        createdAt = nowInstant,
        updatedAt = nowInstant,
    )

    @Test
    fun onceHappensOnlyOnStartDate() {
        val r = reminder(ScheduleType.ONCE, startDate = LocalDate.of(2026, 9, 22))
        assertEquals(1, ScheduleCalculator.occurrencesOn(r, LocalDate.of(2026, 9, 22)).size)
        assertTrue(ScheduleCalculator.occurrencesOn(r, LocalDate.of(2026, 9, 23)).isEmpty())
    }

    @Test
    fun dailyHappensEveryDayWithAllTimes() {
        val r = reminder(
            ScheduleType.DAILY,
            times = listOf(LocalTime.of(9, 0), LocalTime.of(15, 0), LocalTime.of(21, 0)),
        )
        assertEquals(3, ScheduleCalculator.occurrencesOn(r, LocalDate.of(2026, 9, 22)).size)
        assertEquals(3, ScheduleCalculator.occurrencesOn(r, LocalDate.of(2026, 10, 1)).size)
    }

    @Test
    fun weeklyHappensOnlyOnSelectedWeekdays() {
        // 22.09.2026 — вторник (DayOfWeek.TUESDAY).
        val r = reminder(
            ScheduleType.WEEKLY,
            weekdays = listOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY),
        )
        assertEquals(1, ScheduleCalculator.occurrencesOn(r, LocalDate.of(2026, 9, 22)).size)
        assertEquals(1, ScheduleCalculator.occurrencesOn(r, LocalDate.of(2026, 9, 25)).size)
        assertTrue(ScheduleCalculator.occurrencesOn(r, LocalDate.of(2026, 9, 23)).isEmpty())
    }

    @Test
    fun intervalDaysRespectsStart() {
        val r = reminder(ScheduleType.INTERVAL_DAYS, intervalDays = 3)
        // 01.09 + 3n: 04.09, 07.09, ... 22.09 (1 + 21 = 22 → 7 интервалов).
        assertEquals(1, ScheduleCalculator.occurrencesOn(r, LocalDate.of(2026, 9, 22)).size)
        assertTrue(ScheduleCalculator.occurrencesOn(r, LocalDate.of(2026, 9, 23)).isEmpty())
    }

    @Test
    fun endDateStopsSchedule() {
        val r = reminder(ScheduleType.DAILY, endDate = LocalDate.of(2026, 9, 22))
        assertEquals(1, ScheduleCalculator.occurrencesOn(r, LocalDate.of(2026, 9, 22)).size)
        assertTrue(ScheduleCalculator.occurrencesOn(r, LocalDate.of(2026, 9, 23)).isEmpty())
        assertNull(ScheduleCalculator.nextOccurrenceAfter(r, LocalDateTime.of(2026, 9, 23, 0, 0)))
    }

    @Test
    fun maxOccurrencesCutsOff() {
        val r = reminder(
            ScheduleType.DAILY,
            times = listOf(LocalTime.of(9, 0), LocalTime.of(15, 0)),
            maxOccurrences = 3,
        )
        // День 1: оба срабатывания, день 2: только 9:00, дальше — пусто.
        assertEquals(2, ScheduleCalculator.occurrencesOn(r, LocalDate.of(2026, 9, 1)).size)
        assertEquals(1, ScheduleCalculator.occurrencesOn(r, LocalDate.of(2026, 9, 2)).size)
        assertEquals(LocalTime.of(9, 0), ScheduleCalculator.occurrencesOn(r, LocalDate.of(2026, 9, 2)).first().toLocalTime())
        assertTrue(ScheduleCalculator.occurrencesOn(r, LocalDate.of(2026, 9, 3)).isEmpty())
    }

    @Test
    fun nextOccurrenceSkipsPastTimes() {
        val r = reminder(
            ScheduleType.DAILY,
            times = listOf(LocalTime.of(9, 0), LocalTime.of(15, 0)),
        )
        val next = ScheduleCalculator.nextOccurrenceAfter(
            r,
            LocalDateTime.of(2026, 9, 22, 10, 0),
        )
        assertEquals(LocalDateTime.of(2026, 9, 22, 15, 0), next)
    }

    @Test
    fun disabledReminderHasNoNext() {
        val r = reminder(ScheduleType.DAILY).copy(enabled = false)
        assertNull(
            ScheduleCalculator.nextOccurrenceAfter(r, LocalDateTime.of(2026, 9, 22, 10, 0)),
        )
    }
}
