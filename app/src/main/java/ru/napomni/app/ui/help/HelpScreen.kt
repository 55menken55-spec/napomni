package ru.napomni.app.ui.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/**
 * «Инструкция» — 10 глав простым языком, офлайн (ТЗ: FR-12).
 * Главы 5 и 10 содержат рабочие кнопки проверки разрешений.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    var expanded by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Инструкция") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(HelpContent.chapters) { chapter ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = if (expanded == chapter.number) null else chapter.number }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Глава ${chapter.number}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                chapter.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (expanded == chapter.number) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                        AnimatedVisibility(visible = expanded == chapter.number) {
                            Column {
                                Spacer(Modifier.height(8.dp))
                                Text(chapter.text, style = MaterialTheme.typography.bodyLarge)
                                if (chapter.number == 5 || chapter.number == 10) {
                                    Spacer(Modifier.height(12.dp))
                                    PermissionChecksBlock()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Проверка разрешений прямо из инструкции (главы 5 и 10). */
@Composable
fun PermissionChecksBlock() {
    val context = LocalContext.current
    // Состояние перечитывается при каждом входе в блок — кнопка «Проверить снова».
    var tick by remember { mutableStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val rows = listOf(
            Triple("Уведомления", PermissionChecks.notificationsAllowed(context), "android.settings.APP_NOTIFICATION_SETTINGS"),
            Triple("Точные будильники", PermissionChecks.exactAlarmsAllowed(context), "android.settings.REQUEST_SCHEDULE_EXACT_ALARM"),
            Triple("Поверх экрана блокировки", PermissionChecks.fullScreenAllowed(context), "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION"),
            Triple("Без ограничений батареи", PermissionChecks.batteryUnrestricted(context), "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS")
        )
        rows.forEach { (label, ok, action) ->
            PermissionRow(label = label, ok = ok, action = action)
        }
        TextButton(onClick = { tick++ }) {
            Text("Проверить снова", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
internal fun PermissionRow(label: String, ok: Boolean, action: String) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = if (ok) "Разрешено" else "Не разрешено",
            tint = if (ok) Color(0xFF2E7D32) else Color(0xFFE65100),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (!ok) {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(action).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }.onFailure {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                }
            }) { Text("Открыть") }
        }
    }
}
