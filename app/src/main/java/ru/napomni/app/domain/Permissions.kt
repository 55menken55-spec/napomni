package ru.napomni.app.domain

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

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
}
