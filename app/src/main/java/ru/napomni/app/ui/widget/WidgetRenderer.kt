package ru.napomni.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import ru.napomni.app.MainActivity
import ru.napomni.app.R
import ru.napomni.app.domain.UpcomingSlots
import ru.napomni.app.domain.formatDate
import ru.napomni.app.domain.formatTime
import java.time.LocalDate

/**
 * Отрисовка виджета: до 4 ближайших срабатываний + кнопка «+» (ТЗ: FR-11).
 * RemoteViews, поэтому простой layout без Compose.
 */
object WidgetRenderer {

    const val EXTRA_NEW_REMINDER = "ru.napomni.app.extra.NEW_REMINDER"

    fun render(context: Context, slots: List<UpcomingSlots.Slot>) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, WidgetProvider::class.java))
        if (ids.isEmpty()) return

        val views = RemoteViews(context.packageName, R.layout.widget_next)
        val lines = listOf(R.id.widget_line1, R.id.widget_line2, R.id.widget_line3, R.id.widget_line4)

        slots.take(4).forEachIndexed { index, slot ->
            views.setTextViewText(lines[index], formatLine(slot))
            views.setViewVisibility(lines[index], View.VISIBLE)
        }
        lines.drop(slots.size.coerceAtMost(4)).forEach { views.setViewVisibility(it, View.GONE) }

        // Кнопка «+» — сразу создать напоминание.
        val addIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_NEW_REMINDER, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val addPending = PendingIntent.getActivity(
            context, 4000, addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_add, addPending)

        // Тап по списку — открыть приложение.
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context, 4001, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_lines, openPending)

        ids.forEach { manager.updateAppWidget(it, views) }
    }

    /** «Сегодня, 15:00 — Таблетки» / «24.09, 15:00 — Таблетки». */
    private fun formatLine(slot: UpcomingSlots.Slot): String {
        val today = LocalDate.now()
        val day = when (slot.at.toLocalDate()) {
            today -> "Сегодня"
            today.plusDays(1) -> "Завтра"
            else -> formatDate(slot.at.toLocalDate())
        }
        return "$day, ${formatTime(slot.at.toLocalTime())} — ${slot.title}"
    }
}
