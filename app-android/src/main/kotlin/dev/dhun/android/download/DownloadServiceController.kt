package dev.dhun.android.download

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.dhun.core.DownloadState
import dev.dhun.core.DownloadedTrack
import dev.dhun.download.DownloadManager
import dev.dhun.download.DownloadRepository
import dev.dhun.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Bridge between the [DhunDownloadService] foreground-service host and the
 * shared [DownloadManager] (ADR-006).
 *
 * Lifecycle:
 *  - The service is started by [ensureRunning] on the first enqueue (or by
 *    the controller itself when it discovers in-flight rows on attach — e.g.
 *    after a process restart).
 *  - The service is stopped when there is no active work, i.e. no row
 *    in [DownloadState.DOWNLOADING] and no [DownloadState.QUEUED] row.
 *  - The controller owns a [SupervisorJob] bound to the service lifetime;
 *    when the service is destroyed, in-flight download jobs are cancelled
 *    cleanly. The repository remains the source of truth, so the next
 *    enqueue (or attach) re-arms the queue.
 *
 * This class is the *only* place that touches the foreground notification
 * from download state, so the service stays a thin Android shell.
 */
class DownloadServiceController : KoinComponent {

    private val downloadManager: DownloadManager by inject()
    private val repository: DownloadRepository by inject()

    private var service: DhunDownloadService? = null
    private var serviceScope: CoroutineScope? = null
    private var stateJob: Job? = null

    /** Called by [DhunDownloadService.onCreate]. */
    fun attach(service: DhunDownloadService) {
        if (this.service != null) return
        this.service = service
        service.ensureChannel()
        // Promote to foreground immediately — Android requires startForeground
        // to be called from onCreate (or shortly after startService) for
        // services with a foregroundServiceType. The initial notification
        // will be replaced as soon as the state collector emits.
        service.startForegroundCompat(service.buildStartingNotification())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        serviceScope = scope
        observeState(scope)
        // Pick up any work that survived a process restart (PAUSED/QUEUED
        // rows). The manager's bounded pool (3 slots) and resume logic
        // handle re-arming.
        scope.launch {
            val existing = repository.getAll()
            existing.filter {
                it.downloadState == DownloadState.QUEUED || it.downloadState == DownloadState.PAUSED
            }.forEach { row ->
                runCatching { downloadManager.resume(row.trackId) }
            }
        }
    }

    /** Called by [DhunDownloadService.onDestroy]. */
    fun detach() {
        stateJob?.cancel()
        stateJob = null
        serviceScope?.cancel()
        serviceScope = null
        service = null
    }

    private fun observeState(scope: CoroutineScope) {
        stateJob = scope.launch {
            downloadManager.downloads
                .distinctUntilChanged()
                .collect { rows -> onState(rows) }
        }
    }

    private fun onState(rows: List<DownloadedTrack>) {
        val svc = service ?: return
        val active = rows.count { it.downloadState == DownloadState.DOWNLOADING }
        val queued = rows.count { it.downloadState == DownloadState.QUEUED }
        if (active == 0 && queued == 0) {
            // No work to keep the service alive for — drop foreground and
            // stop. The repository persists state, so a future enqueue
            // (or a process restart that re-attaches the controller) will
            // re-arm the queue.
            svc.stopForegroundCompat()
            return
        }
        svc.postNotification(buildProgressNotification(svc, active, queued))
    }

    private fun buildProgressNotification(
        ctx: Context,
        active: Int,
        queued: Int,
    ): Notification {
        val title = when {
            active == 0 -> "Queued $queued download${if (queued == 1) "" else "s"}"
            queued == 0 -> "Downloading $active track${if (active == 1) "" else "s"}"
            else -> "Downloading $active · $queued queued"
        }
        return NotificationCompat.Builder(ctx, DhunDownloadService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    /**
     * Ensure the foreground service is running. Safe to call from any
     * thread — startForegroundService is non-blocking.
     */
    fun ensureRunning(context: Context) {
        val intent = Intent(context, DhunDownloadService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    companion object {
        /** Action: the service is being started by the controller after enqueue. */
        const val ACTION_ENQUEUE = "dev.dhun.android.download.action.ENQUEUE"
    }
}
