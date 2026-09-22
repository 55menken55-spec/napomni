package ru.napomni.app.domain

import ru.napomni.app.data.model.Occurrence
import ru.napomni.app.data.model.OccurrenceStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** Статус дня для календаря (ТЗ, FR-6.2). */
enum class DayStatus { NONE, ALL_DONE, PARTIAL, HAS_MISSED }

/** Сводка за период (ТЗ, FR-6.4). */
data class Summary(
    val total: Int = 0,
    val done: Int = 0,
    val missed: Int = 0,
    val skipped: Int = 0,
    /** Сколько срабатываний откладывали хотя бы раз. */
    val snoozed: Int = 0,
    /** Процент выполнения (дисциплина). */
    val discipline: Int = 0,
)

/** Статистика за один период (7/30/90 дней), ТЗ FR-6.3. */
data class PeriodStat(
    val days: Int,
    val done: Int,
    val total: Int,
    val percent: Int,
)

/** Сводка по напоминанию (ТЗ, FR-6.3). */
data class ReminderStats(
    val reminderId: Long,
    val title: String,
    val done: Int,
    val total: Int,
    val percent: Int,
    /** Дней подряд без пропусков. */
    val streakDays: Int,
    /** Выполнено по неделям (последние 8 недель, старые слева). */
    val weeklyDone: List<Int>,
    /** Разбивка по периодам 7/30/90 дней. */
    val byPeriod: List<PeriodStat> = emptyList(),
)

/**
 * Чистая логика статистики (ТЗ, FR-6). Правила окраски дней:
 *  - серый  — не было срабатываний;
 *  - зелёный — все выполнены;
 *  - красный — есть пропуски (авто «пропущено» или «пропустить»);
 *  - жёлтый — частично (иначе).
 */
object StatsCalculator {

    fun dayStatus(occurrences: List<Occurrence>): DayStatus = when {
        occurrences.isEmpty() -> DayStatus.NONE
        occurrences.any {
            it.status == OccurrenceStatus.MISSED || it.status == OccurrenceStatus.SKIPPED
        } -> DayStatus.HAS_MISSED
        occurrences.all { it.status == OccurrenceStatus.DONE } -> DayStatus.ALL_DONE
        else -> DayStatus.PARTIAL
    }

    /** Окраска дней месяца для календаря. */
    fun monthStatuses(
        occurrences: List<Occurrence>,
        month: YearMonth,
        zone: ZoneId,
    ): Map<LocalDate, DayStatus> =
        occurrences
            .groupBy { LocalDateTime.ofInstant(it.scheduledAt, zone).toLocalDate() }
            .filterKeys { month == YearMonth.from(it) }
            .mapValues { (_, dayOccurrences) -> dayStatus(dayOccurrences) }

    fun summarize(occurrences: List<Occurrence>): Summary {
        val total = occurrences.size
        if (total == 0) return Summary()
        val done = occurrences.count { it.status == OccurrenceStatus.DONE }
        val missed = occurrences.count { it.status == OccurrenceStatus.MISSED }
        val skipped = occurrences.count { it.status == OccurrenceStatus.SKIPPED }
        val snoozed = occurrences.count { it.snoozeCount > 0 }
        return Summary(
            total = total,
            done = done,
            missed = missed,
            skipped = skipped,
            snoozed = snoozed,
            discipline = done * 100 / total,
        )
    }

    /**
     * Дней подряд без пропусков (заканчивая сегодняшним днём).
     * Пустые дни серию не обрывают и считаются.
     * [since] — граница (дата создания напоминания), чтобы серия не «уходила» до создания.
     */
    fun streakDays(
        occurrences: List<Occurrence>,
        now: LocalDateTime,
        zone: ZoneId,
        since: LocalDate? = null,
        maxLookback: Int = 365,
    ): Int {
        val byDay = occurrences
            .groupBy { LocalDateTime.ofInstant(it.scheduledAt, zone).toLocalDate() }
        var day = now.toLocalDate()
        var streak = 0
        while (streak < maxLookback) {
            if (since != null && day.isBefore(since)) break
            val bad = byDay[day].orEmpty().any {
                it.status == OccurrenceStatus.MISSED || it.status == OccurrenceStatus.SKIPPED
            }
            if (bad) break
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** Выполнено по неделям: список длиной [weeks], старые недели слева. */
    fun weeklyDone(
        occurrences: List<Occurrence>,
        now: LocalDateTime,
        zone: ZoneId,
        weeks: Int = 8,
    ): List<Int> {
        val counts = MutableList(weeks) { 0 }
        val today = now.toLocalDate()
        occurrences
            .filter { it.status == OccurrenceStatus.DONE }
            .forEach { occurrence ->
                val daysAgo = java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDateTime.ofInstant(occurrence.scheduledAt, zone).toLocalDate(),
                    today,
                ).toInt()
                if (daysAgo in 0 until weeks * 7) {
                    counts[weeks - 1 - daysAgo / 7]++
                }
            }
        return counts
    }

    /** Сводка по напоминанию за период. */
    fun reminderStats(
        reminderId: Long,
        title: String,
        periodOccurrences: List<Occurrence>,
        allOccurrences: List<Occurrence>,
        now: LocalDateTime,
        zone: ZoneId,
        since: LocalDate? = null,
    ): ReminderStats {
        val done = periodOccurrences.count { it.status == OccurrenceStatus.DONE }
        val total = periodOccurrences.size
        val today = now.toLocalDate()
        val byPeriod = listOf(7, 30, 90).map { days ->
            val from = today.minusDays((days - 1).toLong())
            val inWindow = allOccurrences.filter {
                !LocalDateTime.ofInstant(it.scheduledAt, zone).toLocalDate().isBefore(from)
            }
            val d = inWindow.count { it.status == OccurrenceStatus.DONE }
            val t = inWindow.size
            PeriodStat(
                days = days,
                done = d,
                total = t,
                percent = if (t == 0) 0 else d * 100 / t,
            )
        }
        return ReminderStats(
            reminderId = reminderId,
            title = title,
            done = done,
            total = total,
            percent = if (total == 0) 0 else done * 100 / total,
            streakDays = streakDays(allOccurrences, now, zone, since),
            weeklyDone = weeklyDone(allOccurrences, now, zone),
            byPeriod = byPeriod,
        )
    }
}
