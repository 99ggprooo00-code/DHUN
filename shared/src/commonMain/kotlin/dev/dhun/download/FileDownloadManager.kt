package dev.dhun.download

import dev.dhun.core.DhunResult
import dev.dhun.core.DownloadState
import dev.dhun.core.DownloadedTrack
import dev.dhun.core.Track
import dev.dhun.data.EpochClock
import dev.dhun.extraction.StreamResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

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
    // Serializes enqueue/resume/removal with clearAll so no new job can appear mid-clear.
    private val mutationLock = Mutex()
    private val jobs = HashMap<String, Job>()

    private val _downloads = MutableStateFlow<List<DownloadedTrack>>(emptyList())
    private val _progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())

    init {
        scope.launch { repository.observeAll().collect { _downloads.value = it } }
        scope.launch {
            // An update kills workers but preserves their rows and files. Do
            // not leave those rows pretending that a worker still exists.
            val repairs = runCatching { reconcilePersistedRows() }
                .onFailure { println("DHUN download restart reconciliation failed: ${it.message}") }
                .getOrDefault(emptyList())
            repairs.forEach { launchArtworkRepair(it) }
        }
    }

    override val downloads: StateFlow<List<DownloadedTrack>> = _downloads.asStateFlow()

    override fun observeProgress(trackId: String): Flow<DownloadProgress?> = _progress.map { it[trackId] }

    override suspend fun enqueue(track: Track): Unit = mutationLock.withLock {
        enqueueLocked(track)
    }

    private suspend fun enqueueLocked(track: Track) {
        val existing = repository.get(track.id)
        if (existing?.isCompleted == true) {
            val audioPresent = runCatching {
                storage.exists(existing.localAudioPath) && storage.size(existing.localAudioPath) > 0L
            }.getOrDefault(true) // a transient IO error must not trigger a redownload
            if (audioPresent) {
                val artMissing = existing.localArtworkPath.let { art ->
                    art == null || runCatching { storage.exists(art) }.getOrDefault(true).not()
                }
                if (artMissing) {
                    val row = existing.copy(localArtworkPath = null)
                    if (row != existing) repository.upsert(row)
                    launchArtworkRepair(row)
                }
                return
            }
            // Keep the metadata, but do not let a stale COMPLETED row suppress
            // recovery when its audio file is actually absent or empty.
        }
        // Placeholder row so the download shows immediately; the real path and
        // MIME are written on completion (only known after resolution).
        repository.upsert(placeholder(track))
        launchWorker(track)
    }

    override suspend fun enqueueAll(tracks: List<Track>): Unit = mutationLock.withLock {
        tracks.forEach { enqueueLocked(it) }
    }

    override suspend fun pause(trackId: String): Unit = mutationLock.withLock {
        val row = repository.get(trackId) ?: return@withLock
        if (row.downloadState == DownloadState.COMPLETED) return@withLock
        repository.updateState(trackId, DownloadState.PAUSED)
        stopJob(trackId)
        _progress.value = _progress.value - trackId
    }

    override suspend fun resume(trackId: String): Unit = mutationLock.withLock {
        val row = repository.get(trackId) ?: return@withLock
        if (row.downloadState == DownloadState.COMPLETED) return@withLock
        repository.updateState(trackId, DownloadState.QUEUED)
        launchWorkerFromRow(row)
    }

    override suspend fun cancel(trackId: String): Unit = mutationLock.withLock {
        stopJob(trackId)
        repository.get(trackId)?.let {
            deleteTemporaryFiles(it.trackId, it.mimeType, it.localArtworkPath)
        }
        repository.delete(trackId)
        _progress.value = _progress.value - trackId
    }

    override suspend fun remove(trackId: String): Unit = mutationLock.withLock {
        stopJob(trackId)
        repository.get(trackId)?.let {
            storage.delete(it.localAudioPath)
            it.localArtworkPath?.let { art -> storage.delete(art) }
            deleteTemporaryFiles(it.trackId, it.mimeType, it.localArtworkPath)
        }
        repository.delete(trackId)
        _progress.value = _progress.value - trackId
    }

    override suspend fun clearAll(): Unit = mutationLock.withLock {
        // Stop and join workers before deleting their files. The mutation lock
        // prevents a new enqueue/resume from starting until cleanup is complete.
        val jobsToStop = jobsLock.withLock {
            val snapshot = jobs.values.toList()
            snapshot.forEach { it.cancel() }
            jobs.clear()
            snapshot
        }
        jobsToStop.joinAll()

        repository.getAll().forEach { row ->
            storage.delete(row.localAudioPath)
            row.localArtworkPath?.let { art -> storage.delete(art) }
            // Also remove deterministic temporary paths. A row may point at the
            // final file after a retry while a stale .part file still exists.
            deleteTemporaryFiles(row.trackId, row.mimeType, row.localArtworkPath)
        }
        repository.clearAll()
        _progress.value = emptyMap()
    }

    /* ---------------- internals ------------------------------------------ */

    private suspend fun stopJob(trackId: String) {
        val job = jobsLock.withLock { jobs.remove(trackId) }
        job?.cancel()
        job?.join()
    }

    private suspend fun deleteTemporaryFiles(
        trackId: String,
        mimeType: String,
        previousArtworkPath: String? = null,
    ) {
        val audioPart = storage.audioPath(trackId, mimeType) + ".part"
        storage.delete(audioPart)
        storage.delete(sourceMetadataPath(audioPart))
        listOfNotNull(storage.artPath(trackId), previousArtworkPath).distinct().forEach { art ->
            val artPart = "$art.part"
            storage.delete(artPart)
            storage.delete(sourceMetadataPath(artPart))
        }
    }

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
                // Pinned to IO: the injected scope is Dispatchers.Main on
                // Android — the streaming read loop, storage IO and DB
                // writes must never run there (reference apps run their
                // download executors off-main too: RiMusic's cached pool,
                // InnerTune's DownloadManager executor).
                withContext(Dispatchers.IO) {
                    semaphore.withPermit { downloadOne(track) }
                }
            } catch (t: CancellationException) {
                throw t // pause/cancel: state already set by the caller
            } catch (t: Throwable) {
                // A worker must never die silently: a storage/SQL crash used
                // to leave the row stuck in DOWNLOADING with no trace.
                println("DHUN download worker failed for ${track.id}: ${t.message}")
                runCatching { repository.updateState(track.id, DownloadState.FAILED) }
                _progress.value = _progress.value - track.id
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
                // println reaches logcat on Android and the console on
                // desktop — a FAILED row with no reason is undebuggable.
                println("DHUN download ${track.id}: resolve failed: ${r.error}")
                repository.updateState(track.id, DownloadState.FAILED)
                return
            }
        }
        repository.updateState(track.id, DownloadState.DOWNLOADING)

        val mime = resolved.mimeType.ifBlank { "audio/webm" }
        val final = storage.audioPath(track.id, mime)
        val part = "$final.part"
        prepareTemporaryFile(
            part = part,
            identity = listOf(
                "audio-v1",
                track.id,
                mime,
                resolved.bitrateKbps?.toString().orEmpty(),
                resolved.contentLengthBytes?.toString().orEmpty(),
            ).joinToString("\n"),
        )

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
                storage.delete(sourceMetadataPath(part))
                check(storage.exists(final) && storage.size(final) == result.value) {
                    "committed audio could not be verified"
                }
                val existingArt = storage.artPath(track.id).takeIf {
                    runCatching { storage.exists(it) && storage.size(it) > 0L }.getOrDefault(false)
                }
                val base = DownloadedTrack(
                    trackId = track.id,
                    title = track.title,
                    artistName = track.artistName,
                    albumName = track.albumName,
                    durationSeconds = track.durationSeconds ?: 0,
                    thumbnailUrl = track.thumbnailUrl,
                    localAudioPath = final,
                    localArtworkPath = existingArt,
                    fileSizeBytes = result.value,
                    mimeType = mime,
                    bitrateKbps = resolved.bitrateKbps,
                    downloadState = DownloadState.COMPLETED,
                    downloadedAtEpochMs = clock.nowMs(),
                )
                // Persist COMPLETED before the (best-effort, cancellable)
                // artwork fetch so pause/cancel can never orphan the audio.
                repository.upsert(base)
                println("DHUN download ${track.id}: completed ${result.value} bytes -> $final")
                if (base.localArtworkPath == null) {
                    val art = fetchArtwork(track.thumbnailUrl, track.id)
                    if (art != null) repository.upsert(base.copy(localArtworkPath = art))
                }
                _progress.value = _progress.value - track.id
            }
            is DhunResult.Failure -> {
                println("DHUN download ${track.id}: bytes failed: ${result.error}")
                repository.updateState(track.id, DownloadState.FAILED)
                _progress.value = _progress.value - track.id
            }
        }
    }

    /**
     * Reconcile rows written by a process/update that can no longer have live
     * workers. Only temporary files and download state are changed; library,
     * queue, playlist, favourite and settings rows are not touched.
     */
    private suspend fun reconcilePersistedRows(): List<DownloadedTrack> = mutationLock.withLock {
        val repairs = mutableListOf<DownloadedTrack>()
        repository.getAll().forEach { row ->
            when (row.downloadState) {
                DownloadState.QUEUED, DownloadState.DOWNLOADING ->
                    repository.updateState(row.trackId, DownloadState.PAUSED)
                DownloadState.COMPLETED -> {
                    val audioHealthy = runCatching {
                        storage.exists(row.localAudioPath) && storage.size(row.localAudioPath) > 0L
                    }.getOrNull()
                    if (audioHealthy == false) {
                        repository.updateState(row.trackId, DownloadState.FAILED)
                        return@forEach
                    }
                    if (audioHealthy == null) return@forEach // retry on the next launch

                    var repaired = row
                    val needsVersionedRefresh = row.localArtworkPath != null &&
                        row.localArtworkPath != storage.artPath(row.trackId)
                    row.localArtworkPath?.let { art ->
                        val artHealthy = runCatching { storage.exists(art) && storage.size(art) > 0L }.getOrNull()
                        if (artHealthy == false) {
                            repaired = row.copy(localArtworkPath = null)
                            repository.upsert(repaired)
                        }
                        if (artHealthy == null && !needsVersionedRefresh) return@forEach
                    }
                    if (repaired.localArtworkPath == null || needsVersionedRefresh) repairs += repaired
                }
                DownloadState.PAUSED, DownloadState.FAILED -> Unit
            }
        }
        repairs
    }

    private suspend fun launchArtworkRepair(row: DownloadedTrack): Unit = jobsLock.withLock {
        if (jobs.containsKey(row.trackId)) return
        val job = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    semaphore.withPermit { repairArtwork(row) }
                }
            } catch (t: CancellationException) {
                throw t
            } catch (_: Throwable) {
                // Artwork is best-effort: never turn a healthy completed audio
                // download into FAILED just because its cover cannot be fetched.
                println("DHUN download artwork repair failed for ${row.trackId}")
            } finally {
                jobsLock.withLock { jobs.remove(row.trackId) }
            }
        }
        jobs[row.trackId] = job
    }

    private suspend fun repairArtwork(row: DownloadedTrack) {
        val art = fetchArtwork(row.thumbnailUrl, row.trackId) ?: return
        val current = repository.get(row.trackId) ?: return
        // The user may have removed, restarted or independently repaired the
        // row while the request was in flight. Never overwrite that newer state.
        if (!current.isCompleted || current.localAudioPath != row.localAudioPath ||
            current.localArtworkPath != row.localArtworkPath
        ) return
        repository.upsert(current.copy(localArtworkPath = art))
        val previousArt = row.localArtworkPath
        if (previousArt != null && previousArt != art && isInsideDownloadRoot(previousArt)) {
            // The new version is durably referenced before the old file is
            // removed. A failed refresh never reaches this cleanup.
            storage.delete(previousArt)
            storage.delete("$previousArt.part")
            storage.delete(sourceMetadataPath("$previousArt.part"))
        }
    }

    private fun isInsideDownloadRoot(path: String): Boolean {
        val root = storage.root.trimEnd('/', '\\')
        return path.startsWith("$root/") || path.startsWith("$root\\")
    }

    /**
     * A .part file is resumable only for the same logical source. Older builds
     * wrote no identity record, so their first update safely restarts just the
     * temporary file. Completed audio/artwork and all database rows survive.
     */
    private suspend fun prepareTemporaryFile(part: String, identity: String) {
        val metadata = sourceMetadataPath(part)
        val previous = runCatching { storage.readText(metadata) }.getOrNull()
        if (previous != identity) {
            storage.delete(part)
            storage.writeText(metadata, identity)
        }
    }

    private fun sourceMetadataPath(part: String): String = "$part.source"

    private suspend fun fetchArtwork(thumbnailUrl: String?, trackId: String): String? {
        val art = storage.artPath(trackId)
        if (runCatching { storage.exists(art) && storage.size(art) > 0L }.getOrDefault(false)) return art
        val part = "$art.part"
        val remoteThumbnail = thumbnailUrl?.trim()?.takeIf {
            it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
        }
        val candidates = listOfNotNull(
            remoteThumbnail,
            "https://i.ytimg.com/vi/${safeTrackId(trackId)}/hqdefault.jpg",
        ).distinct()
        for (url in candidates) {
            prepareTemporaryFile(part, "artwork-v1\n$trackId\n$url")
            val result = try {
                downloader.download(url, null, part) { _, _ -> }
            } catch (t: CancellationException) {
                throw t
            }
            if (result is DhunResult.Success) {
                storage.commit(part, art)
                storage.delete(sourceMetadataPath(part))
                if (runCatching { storage.exists(art) && storage.size(art) > 0L }.getOrDefault(false)) {
                    return art
                }
                storage.delete(art)
            }
            // A following candidate has a different identity, so preparation
            // removes any stale bytes before they can be appended to it.
        }
        return null
    }

}
