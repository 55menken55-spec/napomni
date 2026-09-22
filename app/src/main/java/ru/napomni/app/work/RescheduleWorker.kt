package ru.napomni.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ru.napomni.app.NapomniApp

/**
 * Ежедневная страховочная сверка расписания с БД (ТЗ, FR-9.3).
 * Защищает от агрессивных прошивок, «убивающих» будильники.
 */
class RescheduleWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            (applicationContext as NapomniApp).container.rescheduleManager.rescheduleAll()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
