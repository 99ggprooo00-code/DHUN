package dev.dhun.presentation

import dev.dhun.core.DownloadState
import dev.dhun.core.DownloadedTrack
import dev.dhun.core.PlaybackState
import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.download.DownloadManager
import dev.dhun.download.DownloadProgress
import dev.dhun.player.DhunPlayer
import dev.dhun.presentation.library.LibraryViewModel
import dev.dhun.presentation.library.StorageSummary
import dev.dhun.presentation.library.toTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Covers the ADR-006 download-management presentation layer: the storage
 * summary aggregation (counts/bytes by state), batch delete and the
 * pause/resume/cancel action plumbing over [DownloadManager], progress
 * pass-through, and graceful degradation when no download engine is wired.
 * Pure JVM — no platform or network dependency.
 */
class LibraryDownloadsViewModelTest {

    private fun downloaded(
        id: String,
        state: DownloadState,
        bytes: Long,
        at: Long = 1_000L,
    ) = DownloadedTrack(
        trackId = id,
        title = "Song $id",
        artistName = "Artist $id",
        albumName = null,
        durationSeconds = 200,
        thumbnailUrl = null,
        localAudioPath = "/tmp/$id.webm",
        localArtworkPath = null,
        fileSizeBytes = bytes,
        mimeType = "audio/webm",
        bitrateKbps = 160,
        downloadState = state,
        downloadedAtEpochMs = at,
    )

    /** Recording fake: records calls so we can assert VM delegation. */
    private class FakeDownloadManager : DownloadManager {
        val state = MutableStateFlow<List<DownloadedTrack>>(emptyList())
        val progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
        override val downloads = state

        val removed = mutableListOf<String>()
        val paused = mutableListOf<String>()
        val resumed = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        var enqueued = mutableListOf<Track>()
            private set
        var cleared = 0
            private set

        override fun observeProgress(trackId: String): Flow<DownloadProgress?> =
            progress.map { it[trackId] }

        override suspend fun enqueue(track: Track) { enqueued.add(track) }
        override suspend fun enqueueAll(tracks: List<Track>) { tracks.forEach { enqueue(it) } }
        override suspend fun pause(trackId: String) { paused.add(trackId) }
        override suspend fun resume(trackId: String) { resumed.add(trackId) }
        override suspend fun cancel(trackId: String) { cancelled.add(trackId) }
        override suspend fun remove(trackId: String) { removed.add(trackId) }
        override suspend fun clearAll() { cleared++; state.value = emptyList() }
    }

    private class NoopPlayer : DhunPlayer {
        /** (queue track ids, start index, playWhenReady) per prepareQueue call. */
        val prepared = mutableListOf<Triple<List<String>, Int, Boolean>>()

        override val state = MutableStateFlow(PlaybackState.Idle)
        override val currentTrack = MutableStateFlow<Track?>(null)
        override val queue = MutableStateFlow<List<Track>>(emptyList())
        override val currentQueueIndex = MutableStateFlow(-1)
        override val positionMs = MutableStateFlow(0L)
        override val durationMs = MutableStateFlow(0L)
        override val repeatMode = MutableStateFlow(RepeatMode.OFF)
        override val shuffleEnabled = MutableStateFlow(false)
        override val volume = MutableStateFlow(1f)
        override suspend fun prepareQueue(tracks: List<Track>, startIndex: Int, playWhenReady: Boolean) {
            prepared.add(Triple(tracks.map { it.id }, startIndex, playWhenReady))
        }
        override fun addNext(track: Track) {}
        override fun addToQueue(track: Track) {}
        override fun playAt(index: Int) {}
        override fun removeFromQueue(index: Int) {}
        override fun moveInQueue(from: Int, to: Int) {}
        override fun playPause() {}
        override fun next() {}
        override fun previous() {}
        override fun seekTo(positionMs: Long) {}
        override fun setRepeatMode(mode: RepeatMode) {}
        override fun setShuffle(enabled: Boolean) {}
        override fun setVolume(volume: Float) {}
        override fun stop() {}
    }

    private fun dataLayer() = DataLayer(DatabaseFactory.create(DatabaseDriverFactory.inMemory().createDriver()))

    private suspend fun eventually(timeoutMs: Long = 10_000L, check: suspend () -> Boolean) {
        withTimeout(timeoutMs) { while (!check()) delay(20) }
    }

    @Test
    fun storageSummaryAggregatesCountsAndBytesByState(): Unit = runBlocking {
        val dm = FakeDownloadManager()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val vm = LibraryViewModel(
                dataLayer = dataLayer(), player = NoopPlayer(), scope = scope,
                downloadManager = dm,
            )
            dm.state.value = listOf(
                downloaded("a", DownloadState.COMPLETED, 1_000L),
                downloaded("b", DownloadState.COMPLETED, 2_000L),
                downloaded("c", DownloadState.DOWNLOADING, 0L),
                downloaded("d", DownloadState.PAUSED, 0L),
                downloaded("e", DownloadState.FAILED, 0L),
            )
            eventually { vm.storageSummary.value.totalTracks == 5 }

            val s = vm.storageSummary.value
            assertEquals(5, s.totalTracks)
            assertEquals(2, s.completedCount)
            assertEquals(3_000L, s.usedByDownloadsBytes, "only completed tracks count as reclaimed bytes")
            assertEquals(2, s.group(DownloadState.COMPLETED).count)
            assertEquals(1, s.group(DownloadState.DOWNLOADING).count)
            assertEquals(1, s.group(DownloadState.PAUSED).count)
            assertEquals(1, s.group(DownloadState.FAILED).count)
            assertEquals(0, s.group(DownloadState.QUEUED).count)
            // Device space on JVM (user-home volume) should report or degrade — never crash.
            if (s.device != null) {
                assertTrue(s.device!!.totalBytes > 0)
                assertTrue(s.device!!.freeBytes >= 0)
                val frac = s.downloadsFractionOfDevice
                assertTrue(frac == null || frac in 0f..1f)
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun emptyDownloadsYieldEmptySummary(): Unit = runBlocking {
        val dm = FakeDownloadManager()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val vm = LibraryViewModel(
                dataLayer = dataLayer(), player = NoopPlayer(), scope = scope,
                downloadManager = dm,
            )
            eventually { vm.storageSummary.value.totalTracks == 0 }
            val s = vm.storageSummary.value
            assertEquals(0, s.completedCount)
            assertEquals(0L, s.usedByDownloadsBytes)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun batchRemovePauseResumeCancelDelegateToManager(): Unit = runBlocking {
        val dm = FakeDownloadManager()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val vm = LibraryViewModel(
                dataLayer = dataLayer(), player = NoopPlayer(), scope = scope,
                downloadManager = dm,
            )
            vm.removeDownloads(listOf("a", "b"))
            vm.pauseDownload("c")
            vm.resumeDownload("d")
            vm.cancelDownload("e")
            vm.clearDownloads()

            eventually { dm.removed.size == 2 && dm.cleared == 1 }
            assertEquals(listOf("a", "b"), dm.removed)
            assertEquals(listOf("c"), dm.paused)
            assertEquals(listOf("d"), dm.resumed)
            assertEquals(listOf("e"), dm.cancelled)
            assertEquals(1, dm.cleared)

            // Empty batch must be a no-op.
            vm.removeDownloads(emptyList())
            delay(50)
            assertEquals(2, dm.removed.size)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun progressForPassesThroughPerTrackFlow(): Unit = runBlocking {
        val dm = FakeDownloadManager()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val vm = LibraryViewModel(
                dataLayer = dataLayer(), player = NoopPlayer(), scope = scope,
                downloadManager = dm,
            )
            assertNull(vm.progressFor("z").first())
            dm.progress.value = mapOf("z" to DownloadProgress("z", bytesDownloaded = 250, totalBytes = 500))
            val p = vm.progressFor("z").first()
            assertEquals(0.5f, p?.fraction)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun downloadsForUiGroupsActiveAboveCompletedNewestFirst(): Unit = runBlocking {
        val dm = FakeDownloadManager()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val vm = LibraryViewModel(
                dataLayer = dataLayer(), player = NoopPlayer(), scope = scope,
                downloadManager = dm,
            )
            dm.state.value = listOf(
                downloaded("a", DownloadState.COMPLETED, 1_000L, at = 1_000L),
                downloaded("b", DownloadState.COMPLETED, 2_000L, at = 5_000L),
                downloaded("c", DownloadState.DOWNLOADING, 0L, at = 3_000L),
                downloaded("d", DownloadState.QUEUED, 0L, at = 4_000L),
                downloaded("e", DownloadState.PAUSED, 0L, at = 2_000L),
                downloaded("f", DownloadState.FAILED, 0L, at = 2_500L),
            )
            eventually { vm.downloadsForUi.value.totalCount == 6 }

            val ui = vm.downloadsForUi.value
            // Active section: state rank first (DOWNLOADING < QUEUED < PAUSED < FAILED),
            // newest-first within a rank.
            assertEquals(listOf("c", "d", "e", "f"), ui.active.map { it.trackId })
            // Completed section: newest-first regardless of seed order.
            assertEquals(listOf("b", "a"), ui.completed.map { it.trackId })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun playDownloadedQueuesCompletedDownloadsAsContext(): Unit = runBlocking {
        val dm = FakeDownloadManager()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val player = NoopPlayer()
            val vm = LibraryViewModel(
                dataLayer = dataLayer(), player = player, scope = scope,
                downloadManager = dm,
            )
            dm.state.value = listOf(
                downloaded("a", DownloadState.COMPLETED, 1_000L, at = 1_000L),
                downloaded("b", DownloadState.COMPLETED, 2_000L, at = 5_000L),
                downloaded("c", DownloadState.COMPLETED, 3_000L, at = 3_000L),
                downloaded("d", DownloadState.DOWNLOADING, 0L, at = 4_000L),
            )
            eventually { vm.downloadsForUi.value.totalCount == 4 }

            vm.playDownloaded(vm.downloads.value.first { it.trackId == "c" }.toTrack())
            eventually { player.prepared.size == 1 }

            val (queueIds, startIndex, playWhenReady) = player.prepared.first()
            // Queue = completed rows only, newest-first; the downloading row is excluded.
            assertEquals(listOf("b", "c", "a"), queueIds)
            assertEquals(1, startIndex)
            assertTrue(playWhenReady)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun playDownloadedFallsBackToSingleTrackWithoutCompletedRow(): Unit = runBlocking {
        val dm = FakeDownloadManager()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val player = NoopPlayer()
            val vm = LibraryViewModel(
                dataLayer = dataLayer(), player = player, scope = scope,
                downloadManager = dm,
            )
            dm.state.value = listOf(downloaded("d", DownloadState.DOWNLOADING, 0L))
            eventually { vm.downloads.value.size == 1 }

            vm.playDownloaded(Track(id = "d", title = "Song d", artistName = "Artist d"))
            eventually { player.prepared.size == 1 }

            val (queueIds, startIndex, playWhenReady) = player.prepared.first()
            assertEquals(listOf("d"), queueIds)
            assertEquals(0, startIndex)
            assertTrue(playWhenReady)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun nullDownloadManagerDegradesGracefully(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val vm = LibraryViewModel(dataLayer = dataLayer(), player = NoopPlayer(), scope = scope)
            assertFalse(vm.hasDownloads)
            eventually { vm.downloads.value.isEmpty() }
            // Summary still emits (empty), actions are no-ops, progress flow is idle.
            assertEquals(0, vm.storageSummary.value.totalTracks)
            vm.removeDownloads(listOf("a"))
            vm.pauseDownload("a")
            vm.resumeDownload("a")
            vm.cancelDownload("a")
            vm.clearDownloads()
            assertNull(vm.progressFor("a").first())
        } finally {
            scope.cancel()
        }
    }
}
