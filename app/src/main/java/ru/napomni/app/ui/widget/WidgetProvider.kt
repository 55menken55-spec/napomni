package ru.napomni.app.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Провайдер виджета «Ближайшее» (ТЗ: FR-11). Обновления приходят от системы,
 * из приложения (WidgetUpdater.update) и по таймеру раз в 15 минут.
 */
class WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetUpdater.update(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Страховка: любые изменения данных/будильников подтверждают обновление.
        if (intent.action != null) WidgetUpdater.update(context)
    }
}
