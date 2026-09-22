package ru.napomni.app.ui.action

import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.napomni.app.NapomniApp
import ru.napomni.app.data.model.Reminder
import ru.napomni.app.data.model.ReminderImportance
import ru.napomni.app.data.model.ThemeMode
import ru.napomni.app.domain.Limits
import ru.napomni.app.domain.formatTime
import ru.napomni.app.ui.theme.NapomniTheme

/**
 * Экран срабатывания (ТЗ, FR-3, FR-4).
 * alarmMode=true — поверх блокировки со звуком («как будильник»);
 * alarmMode=false — обычный экран с кнопками действий (по тапу на уведомление).
 */
class ReminderActionActivity : ComponentActivity() {

    private val reminder = MutableStateFlow<Reminder?>(null)
    private val presets = MutableStateFlow(listOf(5, 15, 30))
    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    private var reminderId: Long = -1L
    private var occurrenceId: Long = -1L
    private var alarmMode: Boolean = false

    private val stopSoundRunnable = Runnable { stopSound() }
    private val timeoutRunnable = Runnable {
        lifecycleScope.launch {
            container().occurrenceActions.markMissedIfStillPending(reminderId, occurrenceId)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyIntent(intent)
        if (alarmMode) {
            setupAlarmWindow()
            startSoundAndVibration()
            handler.postDelayed(stopSoundRunnable, Limits.ALARM_SOUND_AUTO_STOP_MIN * 60_000)
        }
        handler.postDelayed(timeoutRunnable, Limits.missedWindowMin(alarmMode) * 60_000)

        setContent {
            val current by reminder.collectAsState()
            val currentPresets by presets.collectAsState()
            val themeMode by produceState(initialValue = ThemeMode.SYSTEM) {
                container().settingsRepository.themeMode.collect { value = it }
            }
            NapomniTheme(themeMode = themeMode) {
                ActionContent(
                    reminder = current,
                    presets = currentPresets,
                    alarmMode = alarmMode,
                    onDone = { act { complete() } },
                    onSkip = { act { skip() } },
                    onSnooze = { minutes -> act { snooze(minutes) } },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyIntent(intent)
    }

    override fun onDestroy() {
        stopSound()
        handler.removeCallbacks(stopSoundRunnable)
        handler.removeCallbacks(timeoutRunnable)
        super.onDestroy()
    }

    private fun applyIntent(intent: Intent?) {
        reminderId = intent?.getLongExtra(EXTRA_REMINDER_ID, -1L) ?: -1L
        occurrenceId = intent?.getLongExtra(EXTRA_OCCURRENCE_ID, -1L) ?: -1L
        alarmMode = intent?.getBooleanExtra(EXTRA_ALARM_MODE, false) ?: false
        if (reminderId <= 0) {
            finish()
            return
        }
        lifecycleScope.launch {
            val container = container()
            reminder.value = container.reminderRepository.get(reminderId)
            presets.value = container.settingsRepository.snoozePresets.first()
            if (reminder.value == null) finish()
        }
    }

    private fun act(block: suspend () -> Unit) {
        lifecycleScope.launch {
            block()
            finish()
        }
    }

    private suspend fun complete() =
        container().occurrenceActions.complete(reminderId, occurrenceId)

    private suspend fun skip() =
        container().occurrenceActions.skip(reminderId, occurrenceId)

    private suspend fun snooze(minutes: Int) =
        container().occurrenceActions.snooze(reminderId, occurrenceId, minutes)

    private fun container() = (application as NapomniApp).container

    private fun setupAlarmWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startSoundAndVibration() {
        val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(this, uri)?.also {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.isLooping = true
            it.play()
        }
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0),
        )
    }

    private fun stopSound() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_OCCURRENCE_ID = "occurrence_id"
        const val EXTRA_ALARM_MODE = "alarm_mode"

        fun buildIntent(
            context: Context,
            reminderId: Long,
            occurrenceId: Long,
            alarmMode: Boolean,
        ): Intent = Intent(context, ReminderActionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("napomni://action/$reminderId/$occurrenceId/$alarmMode")
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(EXTRA_ALARM_MODE, alarmMode)
        }
    }
}

@Composable
private fun ActionContent(
    reminder: Reminder?,
    presets: List<Int>,
    alarmMode: Boolean,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onSnooze: (Int) -> Unit,
) {
    var showCustom by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (alarmMode) {
            Icon(
                Icons.Default.Alarm,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            reminder?.title ?: "Напоминание",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        if (!reminder?.details.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                reminder!!.details,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (reminder != null && reminder.importance == ReminderImportance.ALARM) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Важное напоминание",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("Выполнено", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        presets.take(3).forEach { minutes ->
            OutlinedButton(
                onClick = { onSnooze(minutes) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Отложить на $minutes мин")
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(
            onClick = { showCustom = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text("Другой интервал…")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSkip) {
            Text("Пропустить")
        }
    }

    if (showCustom) {
        CustomSnoozeDialog(
            onConfirm = { minutes ->
                showCustom = false
                onSnooze(minutes)
            },
            onDismiss = { showCustom = false },
        )
    }
}

@Composable
private fun CustomSnoozeDialog(
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf("10") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Отложить на сколько минут?") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { text -> value = text.filter(Char::isDigit).take(3) },
                label = { Text("Минут (1–180)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val minutes = value.toIntOrNull()
                    if (minutes != null && minutes in 1..180) onConfirm(minutes)
                },
            ) { Text("Отложить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
