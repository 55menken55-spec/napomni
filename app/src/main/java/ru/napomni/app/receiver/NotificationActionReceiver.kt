package ru.napomni.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.napomni.app.NapomniApp

/** Приёмник кнопок в уведомлении: «Выполнено», «Отложить N», «Пропустить» (ТЗ, FR-3.1, FR-4). */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val occurrenceId = intent.getLongExtra(EXTRA_OCCURRENCE_ID, -1L)
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
        if (reminderId <= 0 || occurrenceId <= 0) return

        val container = (context.applicationContext as NapomniApp).container
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_DONE -> container.occurrenceActions.complete(reminderId, occurrenceId)
                    ACTION_SKIP -> container.occurrenceActions.skip(reminderId, occurrenceId)
                    ACTION_SNOOZE -> container.occurrenceActions.snooze(
                        reminderId,
                        occurrenceId,
                        minutes,
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "ru.napomni.app.action.DONE"
        const val ACTION_SKIP = "ru.napomni.app.action.SKIP"
        const val ACTION_SNOOZE = "ru.napomni.app.action.SNOOZE"

        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_OCCURRENCE_ID = "occurrence_id"
        const val EXTRA_MINUTES = "minutes"
    }
}
