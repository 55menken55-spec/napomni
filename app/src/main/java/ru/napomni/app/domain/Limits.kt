package ru.napomni.app.domain

/** Константы окон и таймингов (ТЗ, FR-3.2, FR-5.3). Настраиваемость — этап M6. */
object Limits {
    /** Через сколько минут незакрытое срабатывание помечается «пропущено» (уведомление). */
    const val MISSED_WINDOW_NOTIFICATION_MIN = 60L

    /** То же для режима «будильник» (сигнал и так громкий). */
    const val MISSED_WINDOW_ALARM_MIN = 30L

    /** Автостоп звука режима «будильник», минут. */
    const val ALARM_SOUND_AUTO_STOP_MIN = 2L

    fun missedWindowMin(alarmMode: Boolean): Long =
        if (alarmMode) MISSED_WINDOW_ALARM_MIN else MISSED_WINDOW_NOTIFICATION_MIN
}
