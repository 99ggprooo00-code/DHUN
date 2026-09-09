package dev.dhun.android.playback

import dev.dhun.core.AlbumDetail
import dev.dhun.core.ArtistPage
import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.HomeFeedPage
import dev.dhun.core.HomeSection
import dev.dhun.core.Lyrics
import dev.dhun.core.PlaylistDetail
import dev.dhun.core.SearchResults
import dev.dhun.core.StreamInfo
import dev.dhun.core.Track
import dev.dhun.innertube.SearchFilter
import dev.dhun.provider.MusicProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression gate for the playback-diagnostics DI wrapper (2026-09-09):
 * resolve outcomes must be recorded (terminal or not) while the wrapped
 * [MusicProvider] result passes through **unchanged**, and every other
 * provider call must forward verbatim (Kotlin `by` delegation).
 */
class ResolveObservingMusicProviderTest {

    private class FakeProvider : MusicProvider {
        var streamCalls = 0
        var searchCalls = 0
        var streamResult: DhunResult<StreamInfo> =
            DhunResult.Failure(DhunError.AuthRequired(detail = "web_remix=AUTH_REQUIRED(gated)"))
        val searchResult: DhunResult<SearchResults> =
            DhunResult.Failure(DhunError.Unknown(causeMessage = "unused"))

        override suspend fun getStreamInfo(videoId: String): DhunResult<StreamInfo> {
            streamCalls++
            return streamResult
        }

        override suspend fun search(query: String, filter: SearchFilter): DhunResult<SearchResults> {
            searchCalls++
            return searchResult
        }

        override suspend fun searchContinuation(continuationToken: String): DhunResult<SearchResults> =
            unsupported()

        override suspend fun searchSuggestions(query: String): DhunResult<List<String>> = unsupported()

        override suspend fun homeFeedPage(): DhunResult<HomeFeedPage> = unsupported()

        override suspend fun homeFeedContinuation(continuationToken: String): DhunResult<HomeFeedPage> = unsupported()

        override suspend fun homeFeed(): DhunResult<List<HomeSection>> = unsupported()

        override suspend fun relatedTracks(videoId: String): DhunResult<List<Track>> = unsupported()

        override suspend fun getLyrics(videoId: String): DhunResult<Lyrics> = unsupported()

        override suspend fun artistPage(browseId: String): DhunResult<ArtistPage> = unsupported()

        override suspend fun albumPage(browseId: String): DhunResult<AlbumDetail> = unsupported()

        override suspend fun playlistPage(browseId: String): DhunResult<PlaylistDetail> = unsupported()

        private fun unsupported(): Nothing = throw UnsupportedOperationException("not exercised by this test")
    }

    private val outcomes = ResolveOutcomeLog(clock = { 1_000_000L }, logger = { })

    @Test
    fun `failure is recorded and the result passes through unchanged`() = runBlocking {
        val delegate = FakeProvider()
        val wrapped = ResolveObservingMusicProvider(delegate, outcomes)
        val result = wrapped.getStreamInfo("v1")
        assertSame(delegate.streamResult, result)
        assertEquals(1, delegate.streamCalls)
        assertNotNull(outcomes.terminalFor("v1"))
    }

    @Test
    fun `success is recorded and suppresses terminality`() = runBlocking {
        val delegate = FakeProvider()
        delegate.streamResult = DhunResult.Success(
            StreamInfo(videoId = "v1", audioUrl = "https://rr.googlevideo.com/videoplayback", mimeType = "audio/webm"),
        )
        val wrapped = ResolveObservingMusicProvider(delegate, outcomes)
        val result = wrapped.getStreamInfo("v1")
        assertSame(delegate.streamResult, result)
        assertNull(outcomes.terminalFor("v1"))
        assertNull(outcomes.last("v1")?.error)
    }

    @Test
    fun `a transient failure is recorded but never terminal`() = runBlocking {
        val delegate = FakeProvider()
        delegate.streamResult = DhunResult.Failure(DhunError.Network(detail = "SocketTimeoutException"))
        val wrapped = ResolveObservingMusicProvider(delegate, outcomes)
        wrapped.getStreamInfo("v1")
        assertTrue(outcomes.last("v1")?.error is DhunError.Network)
        assertNull(outcomes.terminalFor("v1"))
    }

    @Test
    fun `blank video ids still resolve but record nothing`() = runBlocking {
        val delegate = FakeProvider()
        val wrapped = ResolveObservingMusicProvider(delegate, outcomes)
        wrapped.getStreamInfo("")
        assertEquals(1, delegate.streamCalls)
        assertNull(outcomes.last(""))
    }

    @Test
    fun `non-stream calls forward to the delegate`() = runBlocking {
        val delegate = FakeProvider()
        val wrapped = ResolveObservingMusicProvider(delegate, outcomes)
        val expected = delegate.search("query")
        assertSame(expected, wrapped.search("query"))
        assertEquals(1, delegate.searchCalls)
    }
}
