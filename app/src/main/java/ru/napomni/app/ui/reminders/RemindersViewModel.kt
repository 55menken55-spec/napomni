package ru.napomni.app.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.napomni.app.data.alarm.AlarmScheduler
import ru.napomni.app.data.model.NoteColor
import ru.napomni.app.data.model.Reminder
import ru.napomni.app.data.model.ReminderImportance
import ru.napomni.app.data.repository.ReminderRepository
import ru.napomni.app.domain.ScheduleCalculator
import ru.napomni.app.domain.describeNext
import ru.napomni.app.domain.describeSchedule
import java.time.LocalDateTime

/** Строка блока «Сегодня». */
data class TodayEntry(
    val reminderId: Long,
    val time: LocalDateTime,
    val title: String,
    val color: NoteColor,
    val importance: ReminderImportance,
    val categoryId: Long?,
    val past: Boolean,
)

/** Строка общего списка напоминаний. */
data class ReminderListItem(
    val reminder: Reminder,
    val scheduleText: String,
    val nextText: String,
)

data class RemindersUiState(
    val today: List<TodayEntry> = emptyList(),
    val items: List<ReminderListItem> = emptyList(),
)

class RemindersViewModel(
    private val repository: ReminderRepository,
    private val alarmScheduler: AlarmScheduler,
) : ViewModel() {

    val uiState: StateFlow<RemindersUiState> = repository.observeAll()
        .map { reminders -> buildState(reminders) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUiState())

    private fun buildState(reminders: List<Reminder>): RemindersUiState {
        val now = LocalDateTime.now()
        val todayDate = now.toLocalDate()

        val today = reminders
            .filter { it.enabled }
            .flatMap { reminder ->
                ScheduleCalculator.occurrencesOn(reminder, todayDate).map { moment ->
                    TodayEntry(
                        reminderId = reminder.id,
                        time = moment,
                        title = reminder.title,
                        color = reminder.color,
                        importance = reminder.importance,
                        categoryId = reminder.categoryId,
                        past = !moment.isAfter(now),
                    )
                }
            }
            .sortedBy { it.time }

        val items = reminders.map { reminder ->
            val next = ScheduleCalculator.nextOccurrenceAfter(reminder, now)
            val nextText = when {
                !reminder.enabled -> "выключено"
                next == null -> "завершено"
                else -> "ближайшее: ${describeNext(next, now)}"
            }
            ReminderListItem(
                reminder = reminder,
                scheduleText = describeSchedule(reminder),
                nextText = nextText,
            )
        }

        return RemindersUiState(today = today, items = items)
    }

    fun setEnabled(reminder: Reminder, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(reminder.id, enabled)
            if (enabled) {
                alarmScheduler.scheduleNext(reminder.copy(enabled = true))
            } else {
                alarmScheduler.cancelReminder(reminder.id)
            }
        }
    }

    fun delete(reminder: Reminder) {
        viewModelScope.launch {
            alarmScheduler.cancelReminder(reminder.id)
            repository.delete(reminder)
        }
    }
}
