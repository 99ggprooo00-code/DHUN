package dev.dhun.android.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.dhun.android.R
import org.koin.android.ext.android.inject

/**
 * Foreground service host for ADR-006 persistent downloads.
 *
 * The shared [dev.dhun.download.DownloadManager] is the *only* source of
 * truth for download state, queueing, and progress (it lives in `shared/`
 * and is wired by `AppModule`). This service is a *host* — it exists to:
 *  1. Promote the process to foreground with a user-visible notification
 *     so downloads survive app backgrounding and OEM battery savers.
 *  2. Carry a long-lived [kotlinx.coroutines.CoroutineScope] bound to the
 *     service lifetime, so download jobs do not get cancelled when the
 *     host activity is destroyed.
 *  3. Reflect [dev.dhun.download.DownloadManager.downloads] state on the
 *     notification (per-track progress, pause/resume/cancel actions).
 *
 * The service starts on the first [ACTION_ENQUEUE] (or auto-resumes when
 * there are PAUSED/QUEUED rows in the repository) and stops itself when
 * there is no work in flight — see [DownloadServiceController] for the
 * start/stop contract.
 *
 * Foreground-service TYPE: `dataSync` — user-initiated file download, the
 * Android 14 replacement for the legacy "download" type. See ADR-006.
 */
class DhunDownloadService : Service() {

    private val controller: DownloadServiceController by inject()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        controller.attach(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Defensive: if a process restart redelivers the intent before the
        // controller is attached, just promote to foreground and wait for
        // attach() to drive real work.
        controller.handleIntent(intent)
        // START_NOT_STICKY: we do not want the system to recreate the
        // service with a null intent on its own — the repository is the
        // single source of truth, and the controller will re-arm from
        // there on the next enqueue.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        controller.detach()
        super.onDestroy()
    }

    /**
     * Promote the service to foreground. Safe to call multiple times —
     * subsequent calls re-issue the notification with current state.
     */
    internal fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    internal fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    internal fun postNotification(notification: Notification) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    internal fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "DHUN offline download progress" },
        )
    }

    /**
     * Build the *initial* foreground notification (used the moment the
     * service is promoted). Subsequent notifications are built by
     * [DownloadServiceController] from real download state.
     */
    internal fun buildStartingNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Preparing download")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()

    internal companion object {
        const val NOTIFICATION_ID = 100
        const val CHANNEL_ID = "dhun_downloads"
    }
}
