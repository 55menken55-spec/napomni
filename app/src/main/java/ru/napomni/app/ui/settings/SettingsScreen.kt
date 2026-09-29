package ru.napomni.app.ui.settings

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.napomni.app.data.model.ThemeMode
import ru.napomni.app.ui.AppViewModelProvider
import ru.napomni.app.ui.help.PermissionChecksBlock
import ru.napomni.app.ui.widget.WidgetUpdater
import java.time.LocalDate

/** «Настройки»: тема, звук, разрешения, отложить, резервная копия, инструкция (ТЗ: FR-8…FR-12). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenHelp: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showPresets by remember { mutableStateOf(false) }
    var showImportWarning by remember { mutableStateOf(false) }
    var importAfterExport by remember { mutableStateOf(false) }

    val contentResolver = context.contentResolver

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportTo(uri) { u, text ->
                contentResolver.openOutputStream(u)?.use { it.write(text.toByteArray()) }
                    ?: error("не удалось открыть файл")
            }
            if (importAfterExport) {
                importAfterExport = false
                showImportWarning = true
            }
        } else {
            importAfterExport = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFrom(uri) { u ->
                contentResolver.openInputStream(u)?.use { it.readBytes().decodeToString() }
            }
            WidgetUpdater.update(context)
        }
    }

    // Куда вернуть выбранный звук: системный пикер не отдаёт обратно наши extras,
    // поэтому цель запоминаем сами (в v1.1 выбор звука из-за этого терялся).
    var soundTarget by remember { mutableStateOf<String?>(null) }

    val soundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        val value = uri?.toString()
        when (soundTarget) {
            "notifications" -> viewModel.setSoundNotifications(value)
            "alarm" -> viewModel.setSoundAlarm(value)
        }
        soundTarget = null
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Настройки") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsCard("Внешний вид") {
                for (mode in ThemeMode.entries) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = uiState.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "Как в системе"
                                ThemeMode.LIGHT -> "Светлая"
                                ThemeMode.DARK -> "Тёмная"
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            SettingsCard("Звук") {
                SoundRow(
                    label = "Звук уведомлений",
                    value = uiState.soundNotificationsUri,
                    onPick = {
                        soundTarget = "notifications"
                        soundLauncher.launch(ringtoneIntent(uiState.soundNotificationsUri))
                    },
                    onReset = { viewModel.setSoundNotifications(null) }
                )
                SoundRow(
                    label = "Звук «будильника»",
                    value = uiState.soundAlarmUri,
                    onPick = {
                        soundTarget = "alarm"
                        soundLauncher.launch(ringtoneIntent(uiState.soundAlarmUri))
                    },
                    onReset = { viewModel.setSoundAlarm(null) }
                )
                Text(
                    "Если ничего не выбрано — используется звук по умолчанию.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsCard("Разрешения") {
                Text(
                    "Разрешения можно включить позже. Подробности — в «Инструкции», главы 5 и 10.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                PermissionChecksBlock()
            }

            SettingsCard("Проверка уведомлений") {
                Text(
                    "Если напоминание не приходит — нажмите кнопку: появится тестовое уведомление. " +
                        "Нет ни плашки, ни звука — значит дело в разрешении, канале или режиме " +
                        "«Не беспокоить».",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.showTestNotification() }) {
                    Text("Показать тестовое уведомление")
                }
            }

            SettingsCard("Кнопки «Отложить»") {
                Text(
                    "Кнопки времени при отложении: ${uiState.snoozePresets.joinToString(", ")} мин",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showPresets = true }) { Text("Изменить кнопки") }
            }

            SettingsCard("Резервная копия") {
                Text(
                    "Сохраните все данные в файл и восстановите их на другом телефоне (ТЗ: FR-10).",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        exportLauncher.launch("napomni-backup-${LocalDate.now()}.json")
                    }) { Text("Экспорт") }
                    OutlinedButton(onClick = { showImportWarning = true }) { Text("Импорт") }
                }
            }

            SettingsCard("Инструкция") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "10 глав: как создать напоминание, разрешения, виджет…",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenHelp) { Text("Открыть инструкцию") }
            }

            Text(
                "«Напомни», версия 1.0. Работает без интернета.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showPresets) {
        SnoozePresetsDialog(
            initial = uiState.snoozePresets,
            onSave = { value ->
                viewModel.setSnoozePresets(value)
                showPresets = false
            },
            onDismiss = { showPresets = false }
        )
    }

    if (showImportWarning) {
        AlertDialog(
            onDismissRequest = { showImportWarning = false },
            title = { Text("Импорт заменит все данные") },
            text = {
                Text(
                    "Импорт полностью заменит текущие напоминания, заметки и историю. " +
                        "Текущие данные будут удалены.\n\nРекомендуем сначала сделать экспорт."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showImportWarning = false
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }) { Text("Продолжить импорт") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showImportWarning = false
                        importAfterExport = true
                        exportLauncher.launch("napomni-backup-${LocalDate.now()}.json")
                    }) { Text("Сначала экспорт") }
                    TextButton(onClick = { showImportWarning = false }) { Text("Отмена") }
                }
            }
        )
    }
}

private fun ringtoneIntent(current: String?): Intent =
    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Выберите звук")
        putExtra(
            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
            current?.takeIf { it.isNotBlank() }?.let(Uri::parse),
        )
    }

@Composable
private fun SoundRow(label: String, value: String?, onPick: () -> Unit, onReset: () -> Unit) {
    val context = LocalContext.current
    val title = remember(value) {
        if (value == null) "Звук по умолчанию"
        else runCatching {
            RingtoneManager.getRingtone(context, Uri.parse(value))?.getTitle(context) ?: "Выбранный звук"
        }.getOrDefault("Выбранный звук")
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onPick) { Text("Выбрать") }
        if (value != null) {
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = onReset) { Text("Сбросить") }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}


/**
 * Редактор кнопок «Отложить»: 3 значения, каждое 1–180 минут (ТЗ: FR-5.2).
 */
@Composable
private fun SnoozePresetsDialog(
    initial: List<Int>,
    onSave: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val fields = remember {
        List(3) { i -> mutableStateOf(initial.getOrNull(i)?.toString() ?: "") }
    }
    val values = fields.map { it.value.trim() }
    val valid = values.all { it.toIntOrNull() in 1..180 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Кнопки «Отложить»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Три кнопки времени (в минутах, от 1 до 180):",
                    style = MaterialTheme.typography.bodyMedium
                )
                fields.forEachIndexed { index, field ->
                    androidx.compose.material3.OutlinedTextField(
                        value = field.value,
                        onValueChange = { field.value = it.filter(Char::isDigit).take(3) },
                        label = { Text("Кнопка ${index + 1}") },
                        singleLine = true,
                        isError = field.value.trim().toIntOrNull() !in 1..180
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = { onSave(values.map { it.toInt() }) }
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
