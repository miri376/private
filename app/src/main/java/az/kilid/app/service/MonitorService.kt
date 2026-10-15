package az.kilid.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import az.kilid.app.LockActivity
import az.kilid.app.MainActivity
import az.kilid.app.data.KilidRepository
import az.kilid.app.data.Policy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * Ön plandakı tətbiqi izləyir (UsageStatsManager). Bloklanmış tətbiq ön plana çıxanda LockActivity göstərir.
 * Yenidən kilidləmə Timer-ə deyil, saxlanmış bitmə vaxtına və hər dövrədə saat əsaslı yoxlamaya əsaslanır.
 * Android bu xidməti batareya qənaəti və ya istifadəçi "Force stop" ilə dayandıra bilər.
 */
class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (job == null) job = scope.launch { monitorLoop() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun monitorLoop() {
        val repo = KilidRepository.get(this)
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        var lastQuery = System.currentTimeMillis() - 5_000
        var current: String? = null
        var lastLaunchPkg: String? = null
        var lastLaunchAt = 0L

        while (currentCoroutineContext().isActive) {
            val nowMs = System.currentTimeMillis()
            try {
                val events = usm.queryEvents(lastQuery - 500, nowMs)
                val event = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == FOREGROUND_EVENT) {
                        current = event.packageName
                    }
                }
            } catch (_: SecurityException) {
                // İstifadə statistikası icazəsi geri götürülüb; UI bunu göstərir.
            }
            lastQuery = nowMs

            val state = repo.state.value
            val pkg = current
            if (state.onboarded && pkg != null && pkg != packageName) {
                val locked = Policy.isLocked(pkg, state, ZonedDateTime.now(), nowMs)
                val recentlyLaunched = lastLaunchPkg == pkg && nowMs - lastLaunchAt < 2_000
                if (locked && !recentlyLaunched) {
                    launchLock(pkg)
                    lastLaunchPkg = pkg
                    lastLaunchAt = nowMs
                    current = packageName // kilid ekranı indi ön plandadır
                }
            }
            delay(POLL_MS)
        }
    }

    private fun launchLock(pkg: String) {
        val intent = Intent(this, LockActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            .putExtra(LockActivity.EXTRA_PACKAGE, pkg)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // "Digər tətbiqlərin üstündə göstər" icazəsi yoxdursa sistem fon aktivliyini bloklaya bilər.
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Kilid nəzarəti", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Kilid işləyir")
            .setContentText("Seçilmiş tətbiqlərə giriş nəzarət olunur.")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "kilid_monitor"
        private const val NOTIFICATION_ID = 1
        private const val POLL_MS = 600L

        // ACTIVITY_RESUMED (API 29+) və köhnə MOVE_TO_FOREGROUND eyni dəyərə (1) malikdir.
        // Köhnə ad bütün API-lərdə mövcud olduğu üçün onu istifadə edirik.
        @Suppress("DEPRECATION")
        private const val FOREGROUND_EVENT = UsageEvents.Event.MOVE_TO_FOREGROUND
    }
}
