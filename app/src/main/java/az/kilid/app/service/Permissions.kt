package az.kilid.app.service

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import az.kilid.app.data.KilidRepository

data class PermissionState(
    val usageAccess: Boolean,
    val overlay: Boolean,
    val notifications: Boolean,
    val batteryUnrestricted: Boolean
) {
    /** Bloklamanın işləməsi üçün mütləq lazım olan iki icazə. */
    val core: Boolean get() = usageAccess && overlay

    companion object {
        fun read(context: Context): PermissionState {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return PermissionState(
                usageAccess = mode == AppOpsManager.MODE_ALLOWED,
                overlay = Settings.canDrawOverlays(context),
                notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                batteryUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName)
            )
        }
    }
}

object SettingsIntents {
    fun usageAccess(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun overlay(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun battery(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun appNotifications(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

object ServiceController {
    /** Onboarding bitibsə və əsas icazələr varsa nəzarət xidmətini işə salır. */
    fun ensureRunning(context: Context) {
        val state = KilidRepository.get(context).state.value
        if (!state.onboarded || !PermissionState.read(context).core) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        } catch (_: Exception) {
            // Sistem fon başlatmasına icazə verməyibsə, tətbiq növbəti dəfə açılanda yenidən cəhd olunacaq.
        }
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, MonitorService::class.java))
    }
}
