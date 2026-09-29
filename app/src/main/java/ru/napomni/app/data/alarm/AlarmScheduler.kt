package ru.napomni.app.data.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import ru.napomni.app.data.db.NapomniDatabase
import ru.napomni.app.data.model.Reminder
import ru.napomni.app.data.model.ReminderImportance
import ru.napomni.app.domain.ScheduleCalculator
import ru.napomni.app.receiver.AlarmReceiver
import ru.napomni.app.ui.action.ReminderActionActivity
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Планировщик точных срабатываний (ТЗ, FR-3.3, FR-9).
 *
 * Схема будильников:
 *  - regular — ближайшее срабатывание каждого активного напоминания (по одному на напоминание);
 *  - snooze  — отложенное срабатывание (по одному на каждое отложение, FR-4);
 *  - missed  — проверка «пропущено» через окно (FR-5.3).
 */
class AlarmScheduler(
    private val context: Context,
    private val database: NapomniDatabase,
) {
    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Ставит ближайшее регулярное срабатывание напоминания (отменяя прежнее). */
    suspend fun scheduleNext(reminder: Reminder) {
        cancelRegular(reminder.id)
        if (!reminder.enabled) return
        // Напоминание, которого ещё нет в БД (id = 0), ставить нельзя: приёмник отбросит
        // такое срабатывание (см. AlarmReceiver) и уведомление не покажется. Это был
        // главный баг v1.1 — «напоминание сохраняется, а уведомление не приходит».
        if (reminder.id <= 0L) {
            Log.e(TAG, "scheduleNext: у напоминания нет id в БД — будильник не поставлен")
            return
        }
        val next = ScheduleCalculator.nextOccurrenceAfter(reminder, LocalDateTime.now()) ?: return
        schedule(
            reminderId = reminder.id,
            occurrenceId = 0L,
            triggerAt = next,
            alarmMode = reminder.importance == ReminderImportance.ALARM,
            kind = KIND_REGULAR,
            requestCode = regularCode(reminder.id),
        )
    }

    /**
     * Снимает «пустой» регулярный будильник, поставленный версией 1.1 при создании
     * напоминания (когда будильник планировался ещё до записи в БД, с id = 0).
     * Такой будильник срабатывает и молча ничего не делает — его нужно убрать.
     */
    fun cancelOrphanAlarm() {
        alarmManager.cancel(pendingBroadcast(regularCode(0L)))
    }

    /** Ставит отложенное срабатывание (ТЗ, FR-4.3). */
    suspend fun scheduleSnooze(
        reminder: Reminder,
        occurrenceId: Long,
        triggerAt: LocalDateTime,
    ) {
        cancelSnooze(occurrenceId)
        schedule(
            reminderId = reminder.id,
            occurrenceId = occurrenceId,
            triggerAt = triggerAt,
            alarmMode = reminder.importance == ReminderImportance.ALARM,
            kind = KIND_SNOOZE,
            requestCode = snoozeCode(occurrenceId),
        )
    }

    /** Ставит проверку «пропущено» через окно (FR-5.3). */
    suspend fun scheduleMissedCheck(
        reminder: Reminder,
        occurrenceId: Long,
        triggerAt: LocalDateTime,
    ) {
        cancelMissed(occurrenceId)
        schedule(
            reminderId = reminder.id,
            occurrenceId = occurrenceId,
            triggerAt = triggerAt,
            alarmMode = false,
            kind = KIND_MISSED,
            requestCode = missedCode(occurrenceId),
        )
    }

    /** Отменяет все будильники напоминания (при удалении/выключении). */
    suspend fun cancelReminder(reminderId: Long) {
        cancelRegular(reminderId)
        database.occurrenceDao().activeFor(reminderId).forEach { occurrence ->
            cancelSnooze(occurrence.id)
            cancelMissed(occurrence.id)
        }
    }

    private fun cancelRegular(reminderId: Long) {
        alarmManager.cancel(pendingBroadcast(regularCode(reminderId)))
    }

    fun cancelSnooze(occurrenceId: Long) {
        alarmManager.cancel(pendingBroadcast(snoozeCode(occurrenceId)))
    }

    fun cancelMissed(occurrenceId: Long) {
        alarmManager.cancel(pendingBroadcast(missedCode(occurrenceId)))
    }

    private fun schedule(
        reminderId: Long,
        occurrenceId: Long,
        triggerAt: LocalDateTime,
        alarmMode: Boolean,
        kind: String,
        requestCode: Int,
    ) {
        if (reminderId <= 0L) {
            Log.e(TAG, "schedule($kind): пропуск — напоминание без id в БД")
            return
        }
        val triggerAtMillis = triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (triggerAtMillis <= System.currentTimeMillis()) return

        val intent = baseIntent(requestCode).apply {
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(EXTRA_TRIGGER_AT, triggerAtMillis)
            putExtra(EXTRA_KIND, kind)
        }
        val op = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val showIntent = PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, ReminderActionActivity::class.java)
                .putExtra(ReminderActionActivity.EXTRA_REMINDER_ID, reminderId)
                .putExtra(ReminderActionActivity.EXTRA_OCCURRENCE_ID, occurrenceId)
                .putExtra(ReminderActionActivity.EXTRA_ALARM_MODE, alarmMode)
                .setData(Uri.parse("napomni://show/$requestCode")),
            PendingIntent.FLAG_IMMUTABLE,
        )

        try {
            when {
                // Режим «будильник»: самый надёжный способ (иконка будильника, обход Doze).
                alarmMode -> alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                    op,
                )
                // Нет разрешения на точные будильники (Android 12+) — ставим неточный.
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    !alarmManager.canScheduleExactAlarms() -> {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        op,
                    )
                }
                else -> alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    op,
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, op)
        }
    }

    /** Базовый intent: data содержит requestCode, чтобы cancel находил тот же PendingIntent. */
    private fun baseIntent(requestCode: Int): Intent =
        Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            data = Uri.parse("napomni://fire/$requestCode")
        }

    private fun pendingBroadcast(requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        baseIntent(requestCode),
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val TAG = "AlarmScheduler"

        const val ACTION_FIRE = "ru.napomni.app.action.FIRE"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_OCCURRENCE_ID = "occurrence_id"
        const val EXTRA_TRIGGER_AT = "trigger_at"
        const val EXTRA_KIND = "kind"

        const val KIND_REGULAR = "regular"
        const val KIND_SNOOZE = "snooze"
        const val KIND_MISSED = "missed"

        private const val REGULAR_BASE = 1_000
        private const val SNOOZE_BASE = 1_000_000
        private const val MISSED_BASE = 2_000_000
        private const val SPACE = 500_000

        private fun regularCode(reminderId: Long) = REGULAR_BASE + (reminderId % SPACE).toInt()

        private fun snoozeCode(occurrenceId: Long) =
            SNOOZE_BASE + (occurrenceId % SPACE).toInt()

        private fun missedCode(occurrenceId: Long) =
            MISSED_BASE + (occurrenceId % SPACE).toInt()
    }
}
