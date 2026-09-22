package ru.napomni.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.napomni.app.data.model.ThemeMode
import ru.napomni.app.ui.NapomniRoot
import ru.napomni.app.ui.theme.NapomniTheme
import ru.napomni.app.ui.widget.WidgetRenderer

class MainActivity : ComponentActivity() {

    /** Запрос на создание напоминания из виджета «+» (ТЗ: FR-11). */
    private val newReminderRequest = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleWidgetIntent(intent)

        // Первый запуск — экран разрешений (ТЗ: FR-9.4).
        val startOnboarding = runBlocking(Dispatchers.IO) {
            !(application as NapomniApp).container.settingsRepository.onboardingDone.first()
        }

        setContent {
            // Выбранная тема применяется сразу, без перезапуска (ТЗ, FR-8.1).
            val themeMode by produceState(initialValue = ThemeMode.SYSTEM) {
                (application as NapomniApp).container.settingsRepository.themeMode
                    .collect { value = it }
            }
            NapomniTheme(themeMode = themeMode) {
                NapomniRoot(
                    startOnboarding = startOnboarding,
                    newReminderRequest = newReminderRequest
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(WidgetRenderer.EXTRA_NEW_REMINDER, false) == true) {
            newReminderRequest.value = true
            intent.removeExtra(WidgetRenderer.EXTRA_NEW_REMINDER)
        }
    }
}
