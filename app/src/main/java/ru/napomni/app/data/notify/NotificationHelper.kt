package ru.napomni.app.data.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import kotlinx.coroutines.flow.first
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import ru.napomni.app.R
import ru.napomni.app.data.model.Reminder
import ru.napomni.app.domain.PermissionChecks
import ru.napomni.app.receiver.NotificationActionReceiver
import ru.napomni.app.ui.action.ReminderActionActivity

/** Каналы и сборка уведомлений (ТЗ, FR-3). */
class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Звук канала берётся из настроек (ТЗ: FR-8.2). [forceRecreate] — пересоздать
     * каналы после смены звука: система читает звук только при создании канала.
     */
    fun ensureChannels(forceRecreate: Boolean = false) {
        val alarmSound = soundFromPrefs(notifications = false)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationSound = soundFromPrefs(notifications = true)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (forceRecreate) {
            notificationManager.deleteNotificationChannel(CH_REMINDERS)
            notificationManager.deleteNotificationChannel(CH_ALARMS)
            notificationManager.deleteNotificationChannel(CH_MISSED)
        }

        val reminders = NotificationChannel(
            CH_REMINDERS,
            "Напоминания",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Обычные напоминания со звуком"
            setSound(notificationSound, audioAttributes())
            enableVibration(true)
        }
        val alarms = NotificationChannel(
            CH_ALARMS,
            "Важные (будильник)",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Громкие напоминания, игнорируют режим «Не беспокоить»"
            setSound(alarmSound, audioAttributes())
            enableVibration(true)
            setBypassDnd(true)
        }
        val missed = NotificationChannel(
            CH_MISSED,
            "Служебные",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Служебные сообщения (пропущенные)"
        }
        notificationManager.createNotificationChannels(listOf(reminders, alarms, missed))
    }

    /**
     * Строит уведомление о срабатывании с кнопками действий (FR-3.1, FR-4.1).
     * [snoozePresets] — первые два значения выводятся кнопками.
     */
    fun buildReminderNotification(
        reminder: Reminder,
        occurrenceId: Long,
        snoozePresets: List<Int>,
        alarmMode: Boolean,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            (occurrenceId % 100_000).toInt() + 30_000,
            Intent(context, ReminderActionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(ReminderActionActivity.EXTRA_REMINDER_ID, reminder.id)
                putExtra(ReminderActionActivity.EXTRA_OCCURRENCE_ID, occurrenceId)
                putExtra(ReminderActionActivity.EXTRA_ALARM_MODE, false)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(
            context,
            if (alarmMode) CH_ALARMS else CH_REMINDERS,
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(reminder.details.ifBlank { "Напоминание" })
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(contentIntent)

        builder.addAction(
            0,
            "Выполнено",
            actionPending(reminder.id, occurrenceId, NotificationActionReceiver.ACTION_DONE, 0, occurrenceId),
        )
        presets(snoozePresets).forEachIndexed { index, minutes ->
            builder.addAction(
                0,
                "Отложить $minutes мин",
                actionPending(
                    reminder.id,
                    occurrenceId,
                    NotificationActionReceiver.ACTION_SNOOZE,
                    minutes,
                    (occurrenceId % 100_000) + (index + 1) * 7_000,
                ),
            )
        }
        if (alarmMode) {
            builder.setFullScreenIntent(contentIntent, true)
        }
        return builder.build()
    }

    /** URI звука из настроек: null = использовать звук по умолчанию. */
    private fun soundFromPrefs(notifications: Boolean): Uri? = runCatching {
        val settings = ru.napomni.app.data.settings.SettingsRepository(context)
        val uri = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            if (notifications) settings.soundNotificationsUri.first() else settings.soundAlarmUri.first()
        }
        uri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
    }.getOrNull()

    /**
     * Тестовое уведомление: быстрая диагностика, когда «напоминание не приходит».
     * Показывает, что разрешение выдано и канал работает. false — показ запрещён системой.
     */
    fun notifyTest(): Boolean {
        if (!PermissionChecks.notificationsAllowed(context)) return false
        ensureChannels()
        val notification = NotificationCompat.Builder(context, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Проверка уведомлений")
            .setContentText("Если вы это видите и слышите — напоминания будут приходить.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()
        notify(TEST_NOTIFICATION_ID, notification)
        return true
    }

    fun notify(occurrenceId: Long, notification: Notification) {
        notificationManager.notify(occurrenceId.toInt(), notification)
    }

    fun cancel(occurrenceId: Long) {
        notificationManager.cancel(occurrenceId.toInt())
    }

    private fun presets(list: List<Int>): List<Int> =
        if (list.size >= 2) listOf(list[0], list[1]) else listOf(5, 15)

    private fun actionPending(
        reminderId: Long,
        occurrenceId: Long,
        action: String,
        minutes: Int,
        requestCodeSeed: Long,
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(NotificationActionReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(NotificationActionReceiver.EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(NotificationActionReceiver.EXTRA_MINUTES, minutes)
        }
        return PendingIntent.getBroadcast(
            context,
            (requestCodeSeed % 200_000).toInt() + 40_000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun audioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    companion object {
        /** id тестового уведомления из «Настройки» → «Проверка уведомлений». */
        const val TEST_NOTIFICATION_ID = -777L

        const val CH_REMINDERS = "reminders"
        const val CH_ALARMS = "alarms"
        const val CH_MISSED = "missed"
    }
}
