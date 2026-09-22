package ru.napomni.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.napomni.app.NapomniApp

/**
 * Системные события, после которых нужно перепланировать будильники (ТЗ, FR-9.1, FR-9.2):
 *  - перезагрузка телефона (BOOT_COMPLETED);
 *  - смена даты/времени вручную (TIME_SET);
 *  - смена часового пояса (TIMEZONE_CHANGED);
 *  - обновление приложения (MY_PACKAGE_REPLACED).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> Unit
            else -> return
        }

        val container = (context.applicationContext as NapomniApp).container
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                container.rescheduleManager.rescheduleAll()
            } catch (_: Exception) {
                // Страховка WorkManager повторит попытку (FR-9.3).
            } finally {
                pendingResult.finish()
            }
        }
    }
}
