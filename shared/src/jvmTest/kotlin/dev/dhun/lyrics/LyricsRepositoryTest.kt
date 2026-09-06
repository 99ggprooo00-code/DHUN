package dev.dhun.lyrics

import dev.dhun.core.AlbumDetail
import dev.dhun.core.ArtistPage
import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.HomeFeedPage
import dev.dhun.core.HomeSection
import dev.dhun.core.Lyrics
import dev.dhun.core.LyricsLine
import dev.dhun.core.PlaylistDetail
import dev.dhun.core.SearchResults
import dev.dhun.core.StreamInfo
import dev.dhun.core.Track
import dev.dhun.data.LyricsCacheRepository
import dev.dhun.innertube.SearchFilter
import dev.dhun.provider.MusicProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LyricsRepositoryTest {

    private fun track(id: String, title: String = "Song $id", artist: String = "Artist $id") =
        Track(id = id, title = title, artistName = artist, durationSeconds = 240)

    private class FakeLyricsCache : LyricsCacheRepository {
        val storage = mutableMapOf<String, Lyrics>()
        var getCalls = 0
        var putCalls = 0
        var shouldThrowOnGet = false

        override suspend fun get(trackId: String): Lyrics? {
            getCalls++
            if (shouldThrowOnGet) throw RuntimeException("Simulated DB read error")
            return storage[trackId]
        }

        override suspend fun put(trackId: String, lyrics: Lyrics) {
            putCalls++
            if (lyrics !is Lyrics.NotAvailable) {
                storage[trackId] = lyrics
            }
        }

        override suspend fun clear() {
            storage.clear()
        }

        override fun observe(trackId: String): Flow<Lyrics?> =
            MutableStateFlow(storage[trackId])
    }

    private class FakeProvider(
        var lyricsResult: DhunResult<Lyrics> = DhunResult.Success(Lyrics.NotAvailable),
    ) : MusicProvider {
        var getLyricsCalls = 0
        override suspend fun getLyrics(videoId: String): DhunResult<Lyrics> {
            getLyricsCalls++
            return lyricsResult
        }
        override suspend fun search(query: String, filter: SearchFilter) = DhunResult.Success(SearchResults(query))
        override suspend fun searchContinuation(continuationToken: String) = DhunResult.Success(SearchResults(""))
        override suspend fun searchSuggestions(query: String) = DhunResult.Success(emptyList<String>())
        override suspend fun homeFeed() = DhunResult.Success(emptyList<HomeSection>())
        override suspend fun homeFeedPage() = DhunResult.Success(HomeFeedPage())
        override suspend fun homeFeedContinuation(continuationToken: String) = DhunResult.Success(HomeFeedPage())
        override suspend fun relatedTracks(videoId: String) = DhunResult.Success(emptyList<Track>())
        override suspend fun getStreamInfo(videoId: String) = DhunResult.Failure(DhunError.Unavailable())
        override suspend fun artistPage(browseId: String) = DhunResult.Failure(DhunError.Unavailable())
        override suspend fun albumPage(browseId: String) = DhunResult.Failure(DhunError.Unavailable())
        override suspend fun playlistPage(browseId: String) = DhunResult.Failure(DhunError.Unavailable())
    }

    private fun createLrcLib(responseBody: String, statusCode: HttpStatusCode = HttpStatusCode.OK): Pair<LrcLibSource, HttpClient> {
        val engine = MockEngine { _ ->
            respond(
                content = responseBody,
                status = statusCode,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) { install(HttpTimeout) }
        return LrcLibSource(httpClient = client, baseUrl = "https://lrclib.net") to client
    }

    @Test
    fun cacheHitReturnsImmediatelyWithoutCallingSources(): Unit = runBlocking {
        val cache = FakeLyricsCache()
        val existing = Lyrics.Synced(listOf(LyricsLine(10_000L, "Cached line")))
        cache.storage["t1"] = existing
        val provider = FakeProvider(DhunResult.Success(Lyrics.Unsynced("YTM lyrics")))
        val (lrcLib, client) = createLrcLib("""{"syncedLyrics":"[00:10.00]LRCLIB"}""")
        try {
            val repo = LyricsRepository(cache, YouTubeLyricsSource(provider), lrcLib)
            val result = repo.getLyrics(track("t1"))
            assertTrue(result is DhunResult.Success)
            assertEquals(existing, result.value)
            assertEquals(1, cache.getCalls)
            assertEquals(0, provider.getLyricsCalls)
            assertEquals(0, cache.putCalls)
        } finally {
            client.close()
        }
    }

    @Test
    fun cacheMissCallsYtmFirstAndCachesSuccess(): Unit = runBlocking {
        val cache = FakeLyricsCache()
        val ytmLyrics = Lyrics.Unsynced("YTM Unsynced text")
        val provider = FakeProvider(DhunResult.Success(ytmLyrics))
        val (lrcLib, client) = createLrcLib("""{"syncedLyrics":"[00:10.00]LRCLIB"}""")
        try {
            val repo = LyricsRepository(cache, YouTubeLyricsSource(provider), lrcLib)
            val result = repo.getLyrics(track("t2"))
            assertTrue(result is DhunResult.Success)
            assertEquals(ytmLyrics, result.value)
            assertEquals(1, cache.getCalls)
            assertEquals(1, provider.getLyricsCalls)
            assertEquals(1, cache.putCalls)
            assertEquals(ytmLyrics, cache.storage["t2"])
        } finally {
            client.close()
        }
    }

    @Test
    fun ytmUnavailableFallsThroughToLrcLibAndCachesLrcLibSynced(): Unit = runBlocking {
        val cache = FakeLyricsCache()
        val provider = FakeProvider(DhunResult.Success(Lyrics.NotAvailable))
        val lrcJson = """{"id":123,"syncedLyrics":"[00:05.00]First line\n[00:10.00]Second line","plainLyrics":"First line\nSecond line"}"""
        val (lrcLib, client) = createLrcLib(lrcJson)
        try {
            val repo = LyricsRepository(cache, YouTubeLyricsSource(provider), lrcLib)
            val result = repo.getLyrics(track("t3"))
            assertTrue(result is DhunResult.Success)
            val lyrics = result.value
            assertTrue(lyrics is Lyrics.Synced)
            assertEquals(2, lyrics.lines.size)
            assertEquals(5000L, lyrics.lines[0].startTimeMs)
            assertEquals("First line", lyrics.lines[0].text)
            assertEquals(1, cache.putCalls)
            assertEquals(lyrics, cache.storage["t3"])
        } finally {
            client.close()
        }
    }

    @Test
    fun ytmAndLrcLibBothUnavailableReturnsNotAvailableAndDoesNotCache(): Unit = runBlocking {
        val cache = FakeLyricsCache()
        val provider = FakeProvider(DhunResult.Failure(DhunError.Unavailable()))
        val (lrcLib, client) = createLrcLib("{}", statusCode = HttpStatusCode.NotFound)
        try {
            val repo = LyricsRepository(cache, YouTubeLyricsSource(provider), lrcLib)
            val result = repo.getLyrics(track("t4"))
            assertTrue(result is DhunResult.Success)
            assertEquals(Lyrics.NotAvailable, result.value)
            assertEquals(0, cache.putCalls)
            assertNull(cache.storage["t4"])
        } finally {
            client.close()
        }
    }

    @Test
    fun cacheReadExceptionFallsThroughToNetworkGracefully(): Unit = runBlocking {
        val cache = FakeLyricsCache()
        cache.shouldThrowOnGet = true
        val ytmLyrics = Lyrics.Unsynced("Recovered via network")
        val provider = FakeProvider(DhunResult.Success(ytmLyrics))
        val (lrcLib, client) = createLrcLib("{}")
        try {
            val repo = LyricsRepository(cache, YouTubeLyricsSource(provider), lrcLib)
            val result = repo.getLyrics(track("t5"))
            assertTrue(result is DhunResult.Success)
            assertEquals(ytmLyrics, result.value)
        } finally {
            client.close()
        }
    }

    @Test
    fun directCacheReadAndClearHelpers(): Unit = runBlocking {
        val cache = FakeLyricsCache()
        val lyrics = Lyrics.Unsynced("Cached")
        cache.storage["t6"] = lyrics
        val provider = FakeProvider()
        val (lrcLib, client) = createLrcLib("{}")
        try {
            val repo = LyricsRepository(cache, YouTubeLyricsSource(provider), lrcLib)
            assertEquals(lyrics, repo.cached("t6"))
            assertNull(repo.cached("unknown"))
            repo.clearCache()
            assertNull(repo.cached("t6"))
        } finally {
            client.close()
        }
    }
}
