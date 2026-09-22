package ru.napomni.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.napomni.app.NapomniApp
import ru.napomni.app.data.alarm.AlarmScheduler
import ru.napomni.app.ui.action.ReminderActionActivity

/** Приёмник срабатываний будильника (ТЗ, FR-3). */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_FIRE) return

        val reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
        val occurrenceId = intent.getLongExtra(AlarmScheduler.EXTRA_OCCURRENCE_ID, 0L)
        val triggerAt = intent.getLongExtra(AlarmScheduler.EXTRA_TRIGGER_AT, 0L)
        val kind = intent.getStringExtra(AlarmScheduler.EXTRA_KIND) ?: AlarmScheduler.KIND_REGULAR
        if (reminderId <= 0 || triggerAt <= 0) return

        val container = (context.applicationContext as NapomniApp).container
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                if (kind == AlarmScheduler.KIND_MISSED) {
                    // Проверка окна «пропущено» — без показа (FR-5.3).
                    container.occurrenceActions.markMissedIfStillPending(reminderId, occurrenceId)
                } else {
                    val reminder = container.occurrenceActions.onAlarmFired(
                        reminderId = reminderId,
                        triggerAtMillis = triggerAt,
                        kind = kind,
                        wantActivity = true,
                    )
                    if (reminder != null) {
                        // Режим «будильник»: полноэкранный экран поверх блокировки (FR-3.2).
                        context.startActivity(
                            ReminderActionActivity.buildIntent(
                                context = context,
                                reminderId = reminderId,
                                occurrenceId = occurrenceId,
                                alarmMode = true,
                            ),
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
