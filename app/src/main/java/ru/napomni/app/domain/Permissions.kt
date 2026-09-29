package ru.napomni.app.domain

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Пункты блока разрешений: подпись, проверка и переход в нужный раздел системных настроек. */
enum class PermissionKind(val label: String) {
    NOTIFICATIONS("Уведомления"),
    EXACT_ALARMS("Точные будильники"),
    FULL_SCREEN("Поверх экрана блокировки"),
    BATTERY("Без ограничений батареи"),
}

/**
 * Проверки разрешений для экранов «Разрешения» и онбординга (ТЗ: FR-9).
 */
object PermissionChecks {

    /** Уведомления (Android 13+). */
    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    /** Точные будильники и «будильник»-тип срабатывания. */
    fun exactAlarmsAllowed(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        } else true

    /** Полноэкранные «будильники» поверх экрана блокировки. */
    fun fullScreenAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.USE_FULL_SCREEN_INTENT
            ) == PackageManager.PERMISSION_GRANTED

    /** Игнорирование оптимизации батареи (важно для Xiaomi и подобных). */
    fun batteryUnrestricted(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Разрешено ли [kind]. */
    fun allowed(context: Context, kind: PermissionKind): Boolean = when (kind) {
        PermissionKind.NOTIFICATIONS -> notificationsAllowed(context)
        PermissionKind.EXACT_ALARMS -> exactAlarmsAllowed(context)
        PermissionKind.FULL_SCREEN -> fullScreenAllowed(context)
        PermissionKind.BATTERY -> batteryUnrestricted(context)
    }

    /**
     * Системный экран выдачи конкретного разрешения (используется кнопкой «Открыть» и онбордингом).
     * В v1.1 для «поверх экрана блокировки» открывался экран доступа ко всем файлам — исправлено.
     */
    fun settingsIntent(context: Context, kind: PermissionKind): Intent = when (kind) {
        PermissionKind.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

        PermissionKind.EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context))
        } else {
            appDetails(context)
        }

        PermissionKind.FULL_SCREEN -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Отдельного экрана для этого разрешения ниже Android 14 нет.
            Intent(ACTION_MANAGE_APP_FULL_SCREEN_INTENT, packageUri(context))
        } else {
            appDetails(context)
        }

        PermissionKind.BATTERY -> Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            packageUri(context),
        )
    }

    /** Открывает системный экран разрешения, а если его нет — карточку приложения в настройках. */
    fun openSettings(context: Context, kind: PermissionKind) {
        val primary = settingsIntent(context, kind).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(primary) }.onFailure {
            runCatching {
                context.startActivity(
                    appDetails(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    private fun packageUri(context: Context): Uri =
        Uri.fromParts("package", context.packageName, null)

    private fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

    /** Константы в Settings нет в компилируемом SDK — экран полноэкранных уведомлений (Android 14+). */
    private const val ACTION_MANAGE_APP_FULL_SCREEN_INTENT =
        "android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT"
}
