package ru.napomni.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ru.napomni.app.data.model.Occurrence
import ru.napomni.app.data.model.OccurrenceStatus
import ru.napomni.app.data.model.Reminder
import ru.napomni.app.data.repository.OccurrenceRepository
import ru.napomni.app.data.repository.ReminderRepository
import ru.napomni.app.domain.DayStatus
import ru.napomni.app.domain.StatsCalculator
import ru.napomni.app.domain.Summary
import ru.napomni.app.domain.ReminderStats
import ru.napomni.app.domain.formatDate
import ru.napomni.app.domain.formatTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** Строка истории (ТЗ, FR-6.1). */
data class HistoryRow(
    val occurrence: Occurrence,
    val title: String,
    val dateText: String,
    val timeText: String,
    val statusLabel: String,
)

data class StatsUiState(
    val periodDays: Int = 30,
    val summary: Summary = Summary(),
    val reminderStats: List<ReminderStats> = emptyList(),
    val month: YearMonth = YearMonth.now(),
    val dayStatuses: Map<LocalDate, DayStatus> = emptyMap(),
    /** Срабатывания по дням месяца — для диалога дня. */
    val monthOccurrences: Map<LocalDate, List<HistoryRow>> = emptyMap(),
    val history: List<HistoryRow> = emptyList(),
    val historyStatus: OccurrenceStatus? = null,
    val historyReminderId: Long? = null,
    val historyReminderTitle: String = "",
    val hasAnyData: Boolean = false,
)

class StatsViewModel(
    private val reminderRepository: ReminderRepository,
    private val occurrenceRepository: OccurrenceRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val periodDays = MutableStateFlow(30)
    private val month = MutableStateFlow(YearMonth.now())
    private val historyStatus = MutableStateFlow<OccurrenceStatus?>(null)
    private val historyReminder = MutableStateFlow<Long?>(null)

    private data class Src(val reminders: List<Reminder>, val occurrences: List<Occurrence>)

    private val since: Instant = Instant.now().minusSeconds(95L * 24 * 3600)
    private val until: Instant = Instant.now().plusSeconds(2L * 24 * 3600)

    val uiState: StateFlow<StatsUiState> = combine(
        combine(
            reminderRepository.observeAll(),
            occurrenceRepository.observeBetween(since, until),
        ) { reminders, occurrences -> Src(reminders, occurrences) },
        periodDays,
        month,
        historyStatus,
        historyReminder,
    ) { src, period, currentMonth, statusFilter, reminderFilter ->
        buildState(src, period, currentMonth, statusFilter, reminderFilter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setPeriod(days: Int) {
        periodDays.value = days
    }

    fun shiftMonth(delta: Long) {
        month.value = month.value.plusMonths(delta)
    }

    fun setHistoryStatus(status: OccurrenceStatus?) {
        historyStatus.value = status
    }

    fun setHistoryReminder(reminderId: Long?) {
        historyReminder.value = reminderId
    }

    private fun buildState(
        src: Src,
        period: Int,
        currentMonth: YearMonth,
        statusFilter: OccurrenceStatus?,
        reminderFilter: Long?,
    ): StatsUiState {
        val now = LocalDateTime.now()
        val remindersById = src.reminders.associateBy { it.id }

        fun titleOf(occurrence: Occurrence): String =
            remindersById[occurrence.reminderId]?.title
                ?: occurrence.title.ifBlank { "Удалённое напоминание" }

        fun rowOf(occurrence: Occurrence) = HistoryRow(
            occurrence = occurrence,
            title = titleOf(occurrence),
            dateText = formatDate(LocalDateTime.ofInstant(occurrence.scheduledAt, zone).toLocalDate()),
            timeText = formatTime(LocalDateTime.ofInstant(occurrence.scheduledAt, zone).toLocalTime()),
            statusLabel = when (occurrence.status) {
                OccurrenceStatus.DONE -> "выполнено"
                OccurrenceStatus.MISSED -> "пропущено"
                OccurrenceStatus.SKIPPED -> "пропущено вручную"
                OccurrenceStatus.SNOOZED -> "отложено"
                OccurrenceStatus.PENDING -> "ожидает"
            },
        )

        val periodFrom = now.toLocalDate().minusDays((period - 1).toLong())
        val inPeriod = src.occurrences.filter {
            !LocalDateTime.ofInstant(it.scheduledAt, zone).toLocalDate().isBefore(periodFrom)
        }
        val summary = StatsCalculator.summarize(inPeriod)

        val allByReminder = src.occurrences.groupBy { it.reminderId }
        val periodByReminder = inPeriod.groupBy { it.reminderId }
        val reminderStats = allByReminder.keys.map { id ->
            val reminder = remindersById[id]
            StatsCalculator.reminderStats(
                reminderId = id,
                title = reminder?.title
                    ?: allByReminder[id]!!.first().title.ifBlank { "Удалённое напоминание" },
                periodOccurrences = periodByReminder[id].orEmpty(),
                allOccurrences = allByReminder[id].orEmpty(),
                now = now,
                zone = zone,
                since = reminder?.createdAt?.let {
                    LocalDateTime.ofInstant(it, zone).toLocalDate()
                },
            )
        }.sortedBy { it.title.lowercase() }

        val allRows = src.occurrences.map(::rowOf)
        val monthMap = allRows
            .groupBy { LocalDateTime.ofInstant(it.occurrence.scheduledAt, zone).toLocalDate() }
            .filterKeys { YearMonth.from(it) == currentMonth }

        val history = allRows
            .asSequence()
            .filter {
                !LocalDateTime.ofInstant(it.occurrence.scheduledAt, zone).toLocalDate()
                    .isBefore(periodFrom)
            }
            .filter { statusFilter == null || it.occurrence.status == statusFilter }
            .filter { reminderFilter == null || it.occurrence.reminderId == reminderFilter }
            .sortedByDescending { it.occurrence.scheduledAt }
            .take(300)
            .toList()

        return StatsUiState(
            periodDays = period,
            summary = summary,
            reminderStats = reminderStats,
            month = currentMonth,
            dayStatuses = StatsCalculator.monthStatuses(src.occurrences, currentMonth, zone),
            monthOccurrences = monthMap,
            history = history,
            historyStatus = statusFilter,
            historyReminderId = reminderFilter,
            historyReminderTitle = reminderFilter?.let { remindersById[it]?.title ?: "…" } ?: "",
            hasAnyData = src.occurrences.isNotEmpty(),
        )
    }
}
