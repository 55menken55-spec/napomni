package ru.napomni.app

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.napomni.app.data.model.Occurrence
import ru.napomni.app.data.model.OccurrenceStatus
import ru.napomni.app.domain.DayStatus
import ru.napomni.app.domain.StatsCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** Юнит-тесты статистики (ТЗ, FR-6). */
class StatsCalculatorTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val now: LocalDateTime = LocalDateTime.of(2026, 9, 22, 12, 0)

    private fun occ(day: Int, hour: Int, status: OccurrenceStatus, snoozes: Int = 0) = Occurrence(
        reminderId = 1,
        scheduledAt = LocalDateTime.of(2026, 9, day, hour, 0)
            .atZone(zone).toInstant(),
        status = status,
        title = "Таблетки",
        snoozeCount = snoozes,
    )

    @Test
    fun dayStatusRules() {
        assertEquals(DayStatus.NONE, StatsCalculator.dayStatus(emptyList()))
        assertEquals(
            DayStatus.ALL_DONE,
            StatsCalculator.dayStatus(
                listOf(
                    occ(21, 9, OccurrenceStatus.DONE),
                    occ(21, 15, OccurrenceStatus.DONE),
                ),
            ),
        )
        assertEquals(
            DayStatus.HAS_MISSED,
            StatsCalculator.dayStatus(
                listOf(
                    occ(21, 9, OccurrenceStatus.DONE),
                    occ(21, 15, OccurrenceStatus.MISSED),
                ),
            ),
        )
        assertEquals(
            DayStatus.HAS_MISSED,
            StatsCalculator.dayStatus(
                listOf(
                    occ(21, 9, OccurrenceStatus.DONE),
                    occ(21, 15, OccurrenceStatus.SKIPPED),
                ),
            ),
        )
        assertEquals(
            DayStatus.PARTIAL,
            StatsCalculator.dayStatus(
                listOf(
                    occ(21, 9, OccurrenceStatus.DONE),
                    occ(21, 15, OccurrenceStatus.PENDING),
                ),
            ),
        )
    }

    @Test
    fun summarizeCountsAndDiscipline() {
        val list = listOf(
            occ(20, 9, OccurrenceStatus.DONE, snoozes = 1),
            occ(20, 15, OccurrenceStatus.DONE),
            occ(21, 9, OccurrenceStatus.MISSED),
            occ(21, 15, OccurrenceStatus.SKIPPED),
        )
        val s = StatsCalculator.summarize(list)
        assertEquals(4, s.total)
        assertEquals(2, s.done)
        assertEquals(1, s.missed)
        assertEquals(1, s.skipped)
        assertEquals(1, s.snoozed)
        assertEquals(50, s.discipline)
    }

    @Test
    fun streakBreaksOnMissAndCountsCleanDays() {
        // 20 и 21 — чистые, 22-го (сегодня) пропуск → серия 0.
        val broken = listOf(
            occ(20, 9, OccurrenceStatus.DONE),
            occ(22, 9, OccurrenceStatus.MISSED),
        )
        assertEquals(
            0,
            StatsCalculator.streakDays(
                broken,
                now,
                zone,
                since = LocalDate.of(2026, 9, 20),
            ),
        )

        // 20, 21, 22 — чистые, считаем от создания 20-го → 3 дня.
        val clean = listOf(
            occ(20, 9, OccurrenceStatus.DONE),
            occ(21, 9, OccurrenceStatus.DONE),
            occ(22, 9, OccurrenceStatus.DONE),
        )
        assertEquals(
            3,
            StatsCalculator.streakDays(
                clean,
                now,
                zone,
                since = LocalDate.of(2026, 9, 20),
            ),
        )
    }

    @Test
    fun streakDoesNotGoBeforeCreation() {
        val clean = listOf(occ(22, 9, OccurrenceStatus.DONE))
        assertEquals(
            1,
            StatsCalculator.streakDays(clean, now, zone, since = LocalDate.of(2026, 9, 22)),
        )
    }

    @Test
    fun weeklyBuckets() {
        val doneThisWeek = occ(22, 9, OccurrenceStatus.DONE)   // сегодня
        val doneLastWeek = occ(14, 9, OccurrenceStatus.DONE)   // 8 дней назад
        val ignored = occ(21, 9, OccurrenceStatus.MISSED)
        val bars = StatsCalculator.weeklyDone(
            listOf(doneThisWeek, doneLastWeek, ignored),
            now,
            zone,
            weeks = 4,
        )
        assertEquals(listOf(0, 0, 1, 1), bars)
    }

    @Test
    fun monthStatusesFiltersMonth() {
        val list = listOf(
            occ(21, 9, OccurrenceStatus.DONE),
            occ(21, 15, OccurrenceStatus.DONE),
        )
        val map = StatsCalculator.monthStatuses(list, YearMonth.of(2026, 9), zone)
        assertEquals(DayStatus.ALL_DONE, map[LocalDate.of(2026, 9, 21)])
        assertEquals(null, map[LocalDate.of(2026, 9, 20)])
    }
}
