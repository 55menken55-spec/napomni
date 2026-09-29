package ru.napomni.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.napomni.app.domain.PermissionChecks
import ru.napomni.app.domain.PermissionKind

/**
 * Экран разрешений при первом запуске (ТЗ: FR-9.4).
 * Можно пропустить («Настроить позже») — разрешения доступны и из настроек.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { tick++ }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Разрешения") },
                actions = {
                    TextButton(onClick = onDone) { Text("Настроить позже") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Чтобы напоминания приходили точно вовремя, приложению нужны разрешения. " +
                    "Их можно включить сейчас или позже в «Настройках» → «Разрешения».",
                style = MaterialTheme.typography.bodyLarge
            )

            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PermissionRow2(
                        title = PermissionKind.NOTIFICATIONS.label,
                        subtitle = "Напоминания будут показываться на экране",
                        ok = PermissionChecks.notificationsAllowed(context)
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            PermissionChecks.openSettings(context, PermissionKind.NOTIFICATIONS)
                        }
                    }
                    PermissionRow2(
                        title = PermissionKind.EXACT_ALARMS.label,
                        subtitle = "Срабатывание точно в назначенное время",
                        ok = PermissionChecks.exactAlarmsAllowed(context)
                    ) {
                        PermissionChecks.openSettings(context, PermissionKind.EXACT_ALARMS)
                    }
                    PermissionRow2(
                        title = PermissionKind.FULL_SCREEN.label,
                        subtitle = "Важные «будильники» видны всегда",
                        ok = PermissionChecks.fullScreenAllowed(context)
                    ) {
                        PermissionChecks.openSettings(context, PermissionKind.FULL_SCREEN)
                    }
                    PermissionRow2(
                        title = PermissionKind.BATTERY.label,
                        subtitle = "Телефон не «усыпит» приложение (важно для Xiaomi и подобных)",
                        ok = PermissionChecks.batteryUnrestricted(context)
                    ) {
                        PermissionChecks.openSettings(context, PermissionKind.BATTERY)
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Приложение работает без интернета и не передаёт данные никуда. " +
                            "Встроенная «Инструкция» есть в «Настройках».",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Готово", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Настроить позже", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun PermissionRow2(title: String, subtitle: String, ok: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (ok) Color(0xFF2E7D32) else Color(0xFFE65100),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = onFix, enabled = !ok) {
            Text(if (ok) "Готово" else "Включить")
        }
    }
}

