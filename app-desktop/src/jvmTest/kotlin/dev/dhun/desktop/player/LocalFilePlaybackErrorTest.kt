package dev.dhun.desktop.player

import dev.dhun.core.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalFilePlaybackErrorTest {
    private val track = Track("O3-6zB3kg8M", "Song", "Artist")

    @Test
    fun localDownloadsAndCacheFilesDoNotEnterCdnRecovery() {
        for (url in listOf("file:///C:/Program%20Files/song.webm", "C:\\Music\\song.webm", "/tmp/song.webm")) {
            val error = assertNotNull(localFilePlaybackError(track, url))
            assertEquals(track, error.track)
            assertTrue(error.message.contains("local audio file"))
            assertFalse(error.message.contains("CDN"))
            assertTrue(error.detail!!.contains("recovery was not attempted"))
        }
    }

    @Test
    fun remoteStreamsStillUseExistingRecoveryPath() {
        assertNull(localFilePlaybackError(track, "https://example.invalid/audio"))
        assertNull(localFilePlaybackError(track, "HTTP://example.invalid/audio"))
        assertNull(localFilePlaybackError(track, null))
        assertNull(localFilePlaybackError(null, "file:///tmp/song.webm"))
    }
}
