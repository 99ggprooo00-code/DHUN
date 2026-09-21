package dev.dhun.android.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
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
        // The intent exists only to satisfy startForegroundService's
        // contract — the controller attached in onCreate() is what drives
        // real work. START_NOT_STICKY: we do not want the system to
        // recreate the service with a null intent on its own; the
        // repository is the single source of truth, and the next
        // enqueue (or a process restart that re-attaches the controller)
        // re-arms the queue.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        controller.detach()
        super.onDestroy()
    }

    /**
     * Promote the service to foreground. Safe to call multiple times —
     * subsequent calls re-issue the notification with current state.
     *
     * Wrapped in try/catch for Android 14+ FGS policy: targetSdk 35 with
     * `dataSync` type throws ForegroundServiceStartNotAllowedException
     * when the app is not in a valid start state (background start
     * budget exhausted, or start attempted while app is backgrounded).
     * The download engine still runs in-process; the promotion is best-effort.
     */
    internal fun startForegroundCompat(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            // API 34+ ForegroundServiceStartNotAllowedException, SecurityException
            // (missing FOREGROUND_SERVICE_DATA_SYNC permission on some OEM builds),
            // or IllegalStateException. Log and continue — the service still
            // exists, just without foreground priority. The worker pool in
            // FileDownloadManager is not gated on this call.
            Log.w("DHUN", "startForegroundCompat failed (service continues without FGS): ${t.message}", t)
            // Best-effort fallback: try without the type flag (covers OEMs
            // that reject dataSync on older OS levels).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { startForeground(NOTIFICATION_ID, notification) }
            }
        }
    }

    internal fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (t: Throwable) {
            Log.w("DHUN", "stopForeground failed: ${t.message}", t)
        }
        try {
            stopSelf()
        } catch (t: Throwable) {
            Log.w("DHUN", "stopSelf failed: ${t.message}", t)
        }
    }

    internal fun postNotification(notification: Notification) {
        try {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        } catch (t: Throwable) {
            // POST_NOTIFICATIONS denied on API 33+ can throw SecurityException
            // for non-FGS notifications, but FGS notifications are exempt.
            // This path is for the progress updates; a failure here must not
            // abort the download, so we catch and log.
            Log.w("DHUN", "postNotification failed: ${t.message}", t)
        }
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
