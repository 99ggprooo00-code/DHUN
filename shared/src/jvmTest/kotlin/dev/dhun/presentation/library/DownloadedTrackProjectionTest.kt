package dev.dhun.presentation.library

import dev.dhun.core.DownloadState
import dev.dhun.core.DownloadedTrack
import dev.dhun.extraction.localAudioFileUri
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadedTrackProjectionTest {
    private val row = DownloadedTrack(
        trackId = "downloaded",
        title = "Downloaded song",
        artistName = "Artist",
        albumName = "Album",
        durationSeconds = 200,
        thumbnailUrl = "https://images.example/remote.jpg",
        localAudioPath = "/data/downloads/audio/downloaded.webm",
        localArtworkPath = "/data/downloads/art/Already Here #1.jpg",
        fileSizeBytes = 1234,
        mimeType = "audio/webm",
        downloadState = DownloadState.COMPLETED,
    )

    @Test
    fun downloadedPlaybackAndLibraryUseTheSavedCoverBeforeNetwork() {
        val projected = row.toTrack()
        assertEquals(localAudioFileUri(row.localArtworkPath!!), projected.thumbnailUrl)
        assertEquals(row.trackId, projected.id)
        assertEquals(row.title, projected.title)
    }

    @Test
    fun remoteCoverRemainsTheFallbackWhenNoLocalCoverWasSaved() {
        assertEquals(row.thumbnailUrl, row.copy(localArtworkPath = null).toTrack().thumbnailUrl)
        assertEquals(row.thumbnailUrl, row.copy(localArtworkPath = " ").toTrack().thumbnailUrl)
    }
}
