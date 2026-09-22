package ru.napomni.app.ui.reminders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.napomni.app.data.alarm.AlarmScheduler
import ru.napomni.app.data.model.EndMode
import ru.napomni.app.data.model.NoteColor
import ru.napomni.app.data.model.Reminder
import ru.napomni.app.data.model.ReminderImportance
import ru.napomni.app.data.model.ScheduleType
import ru.napomni.app.data.repository.ReminderRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Состояние формы создания/редактирования напоминания (ТЗ, FR-1.1, FR-2). */
data class ReminderFormState(
    val id: Long = 0,
    val title: String = "",
    val details: String = "",
    val scheduleType: ScheduleType = ScheduleType.DAILY,
    /** Времена срабатывания (для ONCE используется onceTime). */
    val times: List<LocalTime> = listOf(LocalTime.of(9, 0)),
    val weekdays: List<DayOfWeek> = emptyList(),
    val intervalDays: String = "3",
    /** Дата начала (для ONCE — дата срабатывания). */
    val startDate: LocalDate = LocalDate.now(),
    val onceTime: LocalTime = LocalTime.of(15, 0),
    val endMode: EndMode = EndMode.NEVER,
    val endDate: LocalDate = LocalDate.now().plusMonths(1),
    val maxOccurrences: String = "30",
    val importance: ReminderImportance = ReminderImportance.NOTIFICATION,
    val color: NoteColor = NoteColor.BLUE,
    val categoryId: Long? = null,
    val enabled: Boolean = true,
    val error: String? = null,
)

class ReminderEditViewModel(
    private val repository: ReminderRepository,
    private val alarmScheduler: AlarmScheduler,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val reminderId: Long = savedStateHandle.get<Long>("id") ?: -1L

    private val _state = MutableStateFlow(ReminderFormState())
    val state: StateFlow<ReminderFormState> = _state.asStateFlow()

    init {
        if (reminderId > 0) {
            viewModelScope.launch {
                repository.get(reminderId)?.let { reminder ->
                    _state.update {
                        ReminderFormState(
                            id = reminder.id,
                            title = reminder.title,
                            details = reminder.details,
                            scheduleType = reminder.scheduleType,
                            times = if (reminder.times.isEmpty()) {
                                listOf(LocalTime.of(9, 0))
                            } else {
                                reminder.times
                            },
                            weekdays = reminder.weekdays,
                            intervalDays = (reminder.intervalDays ?: 3).toString(),
                            startDate = reminder.startDate,
                            onceTime = reminder.times.firstOrNull() ?: LocalTime.of(15, 0),
                            endMode = when {
                                reminder.endDate != null -> EndMode.UNTIL_DATE
                                reminder.maxOccurrences != null -> EndMode.AFTER_COUNT
                                else -> EndMode.NEVER
                            },
                            endDate = reminder.endDate ?: LocalDate.now().plusMonths(1),
                            maxOccurrences = (reminder.maxOccurrences ?: 30).toString(),
                            importance = reminder.importance,
                            color = reminder.color,
                            categoryId = reminder.categoryId,
                            enabled = reminder.enabled,
                        )
                    }
                }
            }
        }
    }

    fun update(transform: (ReminderFormState) -> ReminderFormState) {
        _state.update { transform(it).copy(error = null) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val form = _state.value
            validate(form)?.let { error ->
                _state.update { it.copy(error = error) }
                return@launch
            }
            val now = Instant.now()
            val isNew = form.id == 0L
            val existing = if (isNew) null else repository.get(form.id)
            val reminder = Reminder(
                id = form.id,
                title = form.title.trim(),
                details = form.details.trim(),
                scheduleType = form.scheduleType,
                times = if (form.scheduleType == ScheduleType.ONCE) {
                    listOf(form.onceTime)
                } else {
                    form.times.sorted()
                },
                weekdays = form.weekdays.sortedBy { it.value },
                intervalDays = if (form.scheduleType == ScheduleType.INTERVAL_DAYS) {
                    form.intervalDays.trim().toIntOrNull()
                } else {
                    null
                },
                startDate = form.startDate,
                endDate = if (form.endMode == EndMode.UNTIL_DATE) form.endDate else null,
                maxOccurrences = if (form.endMode == EndMode.AFTER_COUNT) {
                    form.maxOccurrences.trim().toIntOrNull()
                } else {
                    null
                },
                importance = form.importance,
                color = form.color,
                categoryId = form.categoryId,
                enabled = form.enabled,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            repository.upsert(reminder)
            alarmScheduler.scheduleNext(reminder)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val form = _state.value
            if (form.id > 0) {
                alarmScheduler.cancelReminder(form.id)
                repository.get(form.id)?.let { repository.delete(it) }
            }
            onDone()
        }
    }

    private fun validate(form: ReminderFormState): String? {
        if (form.title.isBlank()) return "Введите заголовок напоминания"

        if (form.scheduleType == ScheduleType.ONCE) {
            val moment = LocalDateTime.of(form.startDate, form.onceTime)
            if (!moment.isAfter(LocalDateTime.now())) {
                return "Это время уже прошло — выберите будущее"
            }
        } else {
            if (form.times.isEmpty()) return "Добавьте хотя бы одно время"
            if (form.times.size > 6) return "Максимум 6 времени суток"
        }

        if (form.scheduleType == ScheduleType.WEEKLY && form.weekdays.isEmpty()) {
            return "Выберите дни недели"
        }

        if (form.scheduleType == ScheduleType.INTERVAL_DAYS) {
            val n = form.intervalDays.trim().toIntOrNull()
            if (n == null || n !in 2..365) return "Интервал: от 2 до 365 дней"
        }

        when (form.endMode) {
            EndMode.UNTIL_DATE ->
                if (form.endDate.isBefore(form.startDate)) {
                    return "Дата окончания раньше даты начала"
                }
            EndMode.AFTER_COUNT -> {
                val k = form.maxOccurrences.trim().toIntOrNull()
                if (k == null || k !in 1..9999) return "Число срабатываний: от 1 до 9999"
            }
            EndMode.NEVER -> Unit
        }
        return null
    }
}
