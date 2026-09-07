package dev.dhun.android.download

import android.content.Context
import androidx.core.content.ContextCompat
import dev.dhun.core.Track
import dev.dhun.download.DownloadManager
import dev.dhun.download.DownloadProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android-side decorator around the shared [DownloadManager] (ADR-006).
 *
 * The shared [dev.dhun.download.FileDownloadManager] is the only source of
 * truth for download state, queueing, and progress. This decorator exists
 * solely to ensure the [DhunDownloadService] foreground service is
 * promoted whenever a download is enqueued — so downloads survive the app
 * being backgrounded or killed by OEM battery savers.
 *
 * All other operations (pause, resume, cancel, remove, clearAll) are pure
 * pass-throughs. Observing state is also unchanged: UI and ViewModels
 * continue to read `downloads` directly.
 */
class ForegroundServiceDownloadManager(
    private val context: Context,
    private val delegate: DownloadManager,
    private val controller: DownloadServiceController,
) : DownloadManager {

    override val downloads: StateFlow<List<dev.dhun.core.DownloadedTrack>> = delegate.downloads

    override fun observeProgress(trackId: String): Flow<DownloadProgress?> =
        delegate.observeProgress(trackId)

    override suspend fun enqueue(track: Track) {
        // Start the service BEFORE enqueue so the service is up before
        // the manager's worker fires — otherwise the first worker tick
        // could be cancelled by an activity teardown.
        controller.ensureRunning(context.applicationContext)
        delegate.enqueue(track)
    }

    override suspend fun enqueueAll(tracks: List<Track>) {
        if (tracks.isNotEmpty()) {
            controller.ensureRunning(context.applicationContext)
        }
        delegate.enqueueAll(tracks)
    }

    override suspend fun pause(trackId: String) = delegate.pause(trackId)
    override suspend fun resume(trackId: String) = delegate.resume(trackId)
    override suspend fun cancel(trackId: String) = delegate.cancel(trackId)
    override suspend fun remove(trackId: String) = delegate.remove(trackId)
    override suspend fun clearAll() = delegate.clearAll()
}
