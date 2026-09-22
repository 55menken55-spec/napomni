package ru.napomni.app

import android.app.Application
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.napomni.app.data.alarm.AlarmScheduler
import ru.napomni.app.data.alarm.RescheduleManager
import ru.napomni.app.data.db.NapomniDatabase
import ru.napomni.app.data.notify.NotificationHelper
import ru.napomni.app.data.repository.CategoryRepository
import ru.napomni.app.data.repository.NoteRepository
import ru.napomni.app.data.repository.OccurrenceRepository
import ru.napomni.app.data.repository.ReminderRepository
import ru.napomni.app.data.settings.SettingsRepository
import ru.napomni.app.domain.OccurrenceActions
import ru.napomni.app.work.RescheduleWorker
import java.util.concurrent.TimeUnit

/** Простой контейнер зависимостей (без Hilt — см. ТЗ, раздел 2). */
class AppContainer(context: Context) {
    private val database = NapomniDatabase.get(context)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val reminderRepository = ReminderRepository(database.reminderDao())
    val noteRepository = NoteRepository(database.noteDao())
    val occurrenceRepository = OccurrenceRepository(database.occurrenceDao())
    val categoryRepository = CategoryRepository(
        database.categoryDao(),
        database.noteDao(),
        database.reminderDao(),
    )
    val settingsRepository = SettingsRepository(context.applicationContext)

    val backupManager by lazy {
        ru.napomni.app.domain.backup.BackupManager(database, settingsRepository)
    }
    val notificationHelper = NotificationHelper(context.applicationContext)
    val alarmScheduler = AlarmScheduler(context.applicationContext, database)
    val rescheduleManager = RescheduleManager(database, reminderRepository, alarmScheduler)

    val occurrenceActions = OccurrenceActions(database, alarmScheduler, notificationHelper).apply {
        settingsPresets = { settingsRepository.snoozePresets.first() }
    }
}

class NapomniApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notificationHelper.ensureChannels()

        // Перепланирование при старте приложения (страховка, ТЗ FR-9).
        container.appScope.launch {
            runCatching { container.rescheduleManager.rescheduleAll() }
        }

        // Ежедневная сверка WorkManager — защита от «убивающих» прошивок (FR-9.3).
        val periodic = PeriodicWorkRequestBuilder<RescheduleWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "reschedule-alarms",
            ExistingPeriodicWorkPolicy.KEEP,
            periodic,
        )
    }
}
