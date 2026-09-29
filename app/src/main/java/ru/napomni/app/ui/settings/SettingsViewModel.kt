package ru.napomni.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.napomni.app.data.settings.SettingsRepository
import ru.napomni.app.data.model.ThemeMode
import ru.napomni.app.domain.backup.BackupManager

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val soundNotificationsUri: String? = null,
    val soundAlarmUri: String? = null,
    val snoozePresets: List<Int> = listOf(5, 15, 30),
    val message: String? = null
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val backupManager: BackupManager,
    private val notificationHelper: ru.napomni.app.data.notify.NotificationHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settings.themeMode.collect { mode ->
                _uiState.value = _uiState.value.copy(themeMode = mode)
            }
        }
        viewModelScope.launch {
            settings.soundNotificationsUri.collect { uri ->
                _uiState.value = _uiState.value.copy(soundNotificationsUri = uri)
            }
        }
        viewModelScope.launch {
            settings.soundAlarmUri.collect { uri ->
                _uiState.value = _uiState.value.copy(soundAlarmUri = uri)
            }
        }
        viewModelScope.launch {
            settings.snoozePresets.collect { presets ->
                _uiState.value = _uiState.value.copy(snoozePresets = presets)
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setSoundNotifications(uri: String?) {
        viewModelScope.launch {
            settings.setSoundNotifications(uri)
            // Система читает звук канала только при создании — пересоздать (FR-8.2).
            notificationHelper.ensureChannels(forceRecreate = true)
        }
    }

    fun setSoundAlarm(uri: String?) {
        viewModelScope.launch {
            settings.setSoundAlarm(uri)
            notificationHelper.ensureChannels(forceRecreate = true)
        }
    }

    fun setOnboardingDone() {
        viewModelScope.launch { settings.setOnboardingDone(true) }
    }

    /** Тестовое уведомление — быстрая проверка, что разрешение/канал/звук в порядке. */
    fun showTestNotification() {
        val shown = notificationHelper.notifyTest()
        _uiState.value = _uiState.value.copy(
            message = if (shown) {
                "Тестовое уведомление показано"
            } else {
                "Показ уведомлений запрещён — включите разрешение в настройках"
            },
        )
    }

    fun setSnoozePresets(presets: List<Int>) {
        viewModelScope.launch { settings.setSnoozePresets(presets) }
    }

    /** Экспорт резервной копии JSON (FR-10). Результат — в [SettingsUiState.message]. */
    fun exportTo(uri: Uri, write: (Uri, String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val json = backupManager.exportToJson()
                write(uri, json)
                _uiState.value = _uiState.value.copy(message = "Резервная копия сохранена")
            }.onFailure {
                _uiState.value = _uiState.value.copy(message = "Не удалось сохранить: ${it.message}")
            }
        }
    }

    /** Импорт — полная замена данных (FR-10). */
    fun importFrom(uri: Uri, read: (Uri) -> String?) {
        viewModelScope.launch {
            runCatching {
                val json = read(uri) ?: error("файл не прочитан")
                backupManager.importFromJson(json)
                _uiState.value = _uiState.value.copy(message = "Данные восстановлены из копии")
            }.onFailure {
                _uiState.value = _uiState.value.copy(message = "Не удалось восстановить: ${it.message}")
            }
        }
    }

    fun messageShown() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    suspend fun isOnboardingDone(): Boolean = settings.onboardingDone.first()
}
