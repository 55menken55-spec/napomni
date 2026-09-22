package ru.napomni.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.napomni.app.data.model.ThemeMode

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Настройки приложения (DataStore). ТЗ, FR-8. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SNOOZE_PRESETS = stringPreferencesKey("snooze_presets")
        val ONBOARDING_DONE = androidx.datastore.preferences.core.booleanPreferencesKey(
            "onboarding_done",
        )
        val SOUND_NOTIFICATIONS = stringPreferencesKey("sound_notifications")
        val SOUND_ALARM = stringPreferencesKey("sound_alarm")
    }

    /** Онбординг разрешений пройден (ТЗ, FR-9.4). */
    val onboardingDone: kotlinx.coroutines.flow.Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[Keys.ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingDone(done: Boolean) {
        context.settingsDataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    /** URI звука уведомлений (null — системный по умолчанию), FR-8.2. */
    val soundNotificationsUri: kotlinx.coroutines.flow.Flow<String?> =
        context.settingsDataStore.data.map { prefs -> prefs[Keys.SOUND_NOTIFICATIONS] }

    /** URI звука режима «будильник» (null — системный будильник), FR-8.2. */
    val soundAlarmUri: kotlinx.coroutines.flow.Flow<String?> =
        context.settingsDataStore.data.map { prefs -> prefs[Keys.SOUND_ALARM] }

    suspend fun setSoundNotifications(uri: String?) {
        context.settingsDataStore.edit {
            if (uri == null) it.remove(Keys.SOUND_NOTIFICATIONS) else it[Keys.SOUND_NOTIFICATIONS] = uri
        }
    }

    suspend fun setSoundAlarm(uri: String?) {
        context.settingsDataStore.edit {
            if (uri == null) it.remove(Keys.SOUND_ALARM) else it[Keys.SOUND_ALARM] = uri
        }
    }

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_MODE]
        runCatching { ThemeMode.valueOf(raw ?: "") }.getOrDefault(ThemeMode.SYSTEM)
    }

    /** Пресеты отложения в минутах (ТЗ, FR-4.1; по умолчанию 5/15/30). */
    val snoozePresets: Flow<List<Int>> = context.settingsDataStore.data.map { prefs ->
        val parsed = (prefs[Keys.SNOOZE_PRESETS] ?: DEFAULT_PRESETS)
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..180 }
        if (parsed.isEmpty()) DEFAULT_LIST else parsed
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setSnoozePresets(presets: List<Int>) {
        val cleaned = presets.filter { it in 1..180 }
        context.settingsDataStore.edit {
            it[Keys.SNOOZE_PRESETS] =
                (if (cleaned.isEmpty()) DEFAULT_LIST else cleaned).joinToString(",")
        }
    }

    companion object {
        const val DEFAULT_PRESETS = "5,15,30"
        val DEFAULT_LIST = listOf(5, 15, 30)
    }
}
