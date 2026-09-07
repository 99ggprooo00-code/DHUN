package dev.dhun.download

import dev.dhun.core.DhunResult
import dev.dhun.core.DownloadState
import dev.dhun.core.DownloadedTrack
import dev.dhun.core.Track
import dev.dhun.data.EpochClock
import dev.dhun.extraction.StreamResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Concrete [DownloadManager] (ADR-006) over a [StreamResolver], a
 * [StreamDownloader] and a [DownloadStorage], persisting state via
 * [DownloadRepository]. Bounded worker pool (default 3) with resume,
 * pause/cancel and cleanup. See ADR-006 for the design contract.
 */
class FileDownloadManager(
    private val repository: DownloadRepository,
    private val resolver: StreamResolver,
    private val downloader: StreamDownloader,
    private val storage: DownloadStorage,
    private val scope: CoroutineScope,
    private val clock: EpochClock = EpochClock.System,
    private val maxConcurrent: Int = 3,
) : DownloadManager {

    private val semaphore = Semaphore(maxConcurrent.coerceAtLeast(1))
    private val jobsLock = Mutex()
    private val jobs = HashMap<String, Job>()

    private val _downloads = MutableStateFlow<List<DownloadedTrack>>(emptyList())
    private val _progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())

    init {
        scope.launch { repository.observeAll().collect { _downloads.value = it } }
    }

    override val downloads: StateFlow<List<DownloadedTrack>> = _downloads.asStateFlow()

    override fun observeProgress(trackId: String): Flow<DownloadProgress?> = _progress.map { it[trackId] }

    override suspend fun enqueue(track: Track): Unit {
        val existing = repository.get(track.id)
        if (existing?.isCompleted == true) return
        // Placeholder row so the download shows immediately; the real path and
        // MIME are written on completion (only known after resolution).
        repository.upsert(placeholder(track))
        launchWorker(track)
    }

    override suspend fun enqueueAll(tracks: List<Track>): Unit = tracks.forEach { enqueue(it) }

    override suspend fun pause(trackId: String): Unit {
        val row = repository.get(trackId) ?: return
        if (row.downloadState == DownloadState.COMPLETED) return
        repository.updateState(trackId, DownloadState.PAUSED)
        jobsLock.withLock { jobs.remove(trackId) }?.cancel()
        _progress.value = _progress.value - trackId
    }

    override suspend fun resume(trackId: String): Unit {
        val row = repository.get(trackId) ?: return
        if (row.downloadState == DownloadState.COMPLETED) return
        repository.updateState(trackId, DownloadState.QUEUED)
        launchWorkerFromRow(row)
    }

    override suspend fun cancel(trackId: String): Unit {
        jobsLock.withLock { jobs.remove(trackId) }?.cancel()
        repository.get(trackId)?.let {
            storage.delete(storage.audioPath(trackId, it.mimeType) + ".part")
            storage.delete(storage.artPath(trackId) + ".part")
        }
        repository.delete(trackId)
        _progress.value = _progress.value - trackId
    }

    override suspend fun remove(trackId: String): Unit {
        jobsLock.withLock { jobs.remove(trackId) }?.cancel()
        repository.get(trackId)?.let {
            storage.delete(it.localAudioPath)
            it.localArtworkPath?.let { art -> storage.delete(art) }
        }
        repository.delete(trackId)
        _progress.value = _progress.value - trackId
    }

    override suspend fun clearAll(): Unit {
        jobsLock.withLock {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
        }
        repository.getAll().forEach { row ->
            storage.delete(row.localAudioPath)
            row.localArtworkPath?.let { art -> storage.delete(art) }
        }
        repository.clearAll()
        _progress.value = emptyMap()
    }

    /* ---------------- internals ------------------------------------------ */

    private fun placeholder(track: Track) = DownloadedTrack(
        trackId = track.id,
        title = track.title,
        artistName = track.artistName,
        albumName = track.albumName,
        durationSeconds = track.durationSeconds ?: 0,
        thumbnailUrl = track.thumbnailUrl,
        localAudioPath = storage.audioPath(track.id, "") + ".part",
        fileSizeBytes = 0,
        mimeType = "",
        downloadState = DownloadState.QUEUED,
        downloadedAtEpochMs = clock.nowMs(),
    )

    private suspend fun launchWorker(track: Track): Unit = jobsLock.withLock {
        if (jobs.containsKey(track.id)) return
        val job = scope.launch {
            try {
                semaphore.withPermit { downloadOne(track) }
            } finally {
                jobsLock.withLock { jobs.remove(track.id) }
            }
        }
        jobs[track.id] = job
    }

    private suspend fun launchWorkerFromRow(row: DownloadedTrack): Unit {
        val track = Track(
            id = row.trackId,
            title = row.title,
            artistName = row.artistName,
            albumName = row.albumName,
            durationSeconds = row.durationSeconds,
            thumbnailUrl = row.thumbnailUrl,
        )
        launchWorker(track)
    }

    private suspend fun downloadOne(track: Track): Unit {
        val resolved = when (val r = resolver.resolve(track.id)) {
            is DhunResult.Success -> r.value
            is DhunResult.Failure -> {
                repository.updateState(track.id, DownloadState.FAILED)
                return
            }
        }
        repository.updateState(track.id, DownloadState.DOWNLOADING)

        val mime = resolved.mimeType.ifBlank { "audio/webm" }
        val final = storage.audioPath(track.id, mime)
        val part = "$final.part"

        val result: DhunResult<Long> = try {
            downloader.download(resolved.audioUrl, resolved.userAgent, part) { written, total ->
                _progress.value = _progress.value + (track.id to DownloadProgress(track.id, written, total))
            }
        } catch (t: CancellationException) {
            throw t // pause/cancel: state already set by the caller; keep `.part`
        }

        when (result) {
            is DhunResult.Success -> {
                storage.commit(part, final)
                val base = DownloadedTrack(
                    trackId = track.id,
                    title = track.title,
                    artistName = track.artistName,
                    albumName = track.albumName,
                    durationSeconds = track.durationSeconds ?: 0,
                    thumbnailUrl = track.thumbnailUrl,
                    localAudioPath = final,
                    localArtworkPath = null,
                    fileSizeBytes = result.value,
                    mimeType = resolved.mimeType,
                    bitrateKbps = resolved.bitrateKbps,
                    downloadState = DownloadState.COMPLETED,
                    downloadedAtEpochMs = clock.nowMs(),
                )
                // Persist COMPLETED before the (best-effort, cancellable)
                // artwork fetch so pause/cancel can never orphan the audio.
                repository.upsert(base)
                val art = fetchArtwork(track.thumbnailUrl, track.id)
                if (art != null) repository.upsert(base.copy(localArtworkPath = art))
                _progress.value = _progress.value - track.id
            }
            is DhunResult.Failure -> {
                repository.updateState(track.id, DownloadState.FAILED)
                _progress.value = _progress.value - track.id
            }
        }
    }

    private suspend fun fetchArtwork(thumbnailUrl: String?, trackId: String): String? {
        val url = thumbnailUrl ?: return null
        val art = storage.artPath(trackId)
        val part = "$art.part"
        val result = try {
            downloader.download(url, null, part) { _, _ -> }
        } catch (t: CancellationException) {
            throw t
        }
        return when (result) {
            is DhunResult.Success -> {
                storage.commit(part, art)
                art
            }
            is DhunResult.Failure -> {
                storage.delete(part)
                null
            }
        }
    }

}
