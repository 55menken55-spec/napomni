package ru.napomni.app.ui.widget

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.napomni.app.NapomniApp
import ru.napomni.app.domain.UpcomingSlots

/**
 * Обновление виджета «Ближайшее»: при изменении данных, при открытии приложения
 * и по таймеру раз в 15 минут (ТЗ: FR-11).
 */
object WidgetUpdater {

    fun update(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val container = (appContext as NapomniApp).container
                val reminders = container.reminderRepository.all().filter { it.enabled }
                val slots = UpcomingSlots.upcoming(reminders)
                WidgetRenderer.render(appContext, slots)
            }
        }
    }
}
