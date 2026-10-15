package dev.dhun.lyrics

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.Lyrics
import dev.dhun.core.Track
import dev.dhun.data.LyricsCacheRepository
import dev.dhun.data.SettingsKeys
import dev.dhun.data.SettingsRepository
import dev.dhun.presentation.player.LyricsMatchController
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsMatchTest {
    private val track = Track("song-a", "Song", "Artist", durationSeconds = 240)
    private val timed = Lyrics.Synced(LrcParser.parse("[00:01.123]<00:01.123>Hello <00:02.456>world<00:03.789>"))
    private val match = LyricsMatch("id-1", "lrclib_search", "Song", "Artist", timed, "en", 240.0)

    private class Store : SettingsRepository {
        val values = mutableMapOf<String, String>()
        override suspend fun getString(key: String) = values[key]
        override suspend fun putString(key: String, value: String) { values[key] = value }
        override suspend fun getBoolean(key: String, default: Boolean) = values[key]?.toBooleanStrictOrNull() ?: default
        override suspend fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())
        override suspend fun getInt(key: String, default: Int) = values[key]?.toIntOrNull() ?: default
        override suspend fun putInt(key: String, value: Int) = putString(key, value.toString())
        override suspend fun remove(key: String) { values.remove(key) }
        override fun observeString(key: String): Flow<String?> = MutableStateFlow(values[key])
    }
    private class Cache : LyricsCacheRepository {
        val values = mutableMapOf<String, Lyrics>()
        var cancelRead = false
        var cancelWrite = false
        override suspend fun get(trackId: String): Lyrics? {
            if (cancelRead) throw CancellationException("cancelled cache read")
            return values[trackId]
        }
        override suspend fun put(trackId: String, lyrics: Lyrics) {
            if (cancelWrite) throw CancellationException("cancelled cache write")
            values[trackId] = lyrics
        }
        override suspend fun clear() { values.clear() }
        override fun observe(trackId: String): Flow<Lyrics?> = MutableStateFlow(values[trackId])
    }
    private class Source(private val block: suspend (Track) -> DhunResult<Lyrics>) : LyricsSource {
        var calls = 0
        override suspend fun fetch(track: Track): DhunResult<Lyrics> { calls++; return block(track) }
    }
    private class Candidates(private val block: suspend (Track, String) -> DhunResult<List<LyricsMatch>>) : LyricsCandidateSource {
        var fetchCalls = 0
        var searchCalls = 0
        override suspend fun fetch(track: Track): DhunResult<Lyrics> { fetchCalls++; return DhunResult.Success(Lyrics.NotAvailable) }
        override suspend fun candidates(track: Track, query: String): DhunResult<List<LyricsMatch>> {
            searchCalls++; return block(track, query)
        }
    }
    private fun emptySource() = Source { DhunResult.Success(Lyrics.NotAvailable) }
    private fun store(vararg ids: String) = Store().apply { values[SettingsKeys.LYRICS_PROVIDER_ORDER] = ids.joinToString(",") }

    @Test fun choiceRetainsWordTimingAcrossRecreationCacheClearAndProviderPreferenceChanges() = runTest {
        val settings = store("ytm", "lrclib_search")
        val cache = Cache()
        val source = emptySource()
        val repo = LyricsRepository(cache, source, source, settings, source)
        repo.selectMatch(track.id, match)
        settings.putString(SettingsKeys.LYRICS_PREFERRED_PROVIDER, "ytm")
        repo.clearCache()
        val reopened = LyricsRepository(cache, source, source, settings, source)
        assertEquals(match, reopened.selectedMatch(track.id))
        assertEquals(DhunResult.Success(timed), reopened.getLyrics(track))
        assertEquals(0, source.calls)
        assertNull(reopened.selectedMatch("another-song"))
        reopened.resetMatch(track.id)
        assertNull(reopened.selectedMatch(track.id))
        assertEquals(DhunResult.Success(Lyrics.NotAvailable), reopened.getLyrics(track))
        assertTrue(source.calls > 0)
    }

    @Test fun playbackShortCircuitsButExplicitSearchReturnsMultipleCandidatesAndPartialFailure() = runTest {
        val plain = Source { DhunResult.Success(Lyrics.Unsynced("plain")) }
        val synced = Source { DhunResult.Success(timed) }
        val candidates = Candidates { _, query ->
            assertEquals("manual query", query)
            DhunResult.Success(listOf(match, match.copy(id = "alternate", title = "Different recording")))
        }
        val failed = Source { throw IllegalStateException("offline") }
        val settings = store("ytm", "lrclib", "lrclib_search", "kugou")
        val repo = LyricsRepository(Cache(), plain, synced, settings, candidates, failed)
        assertEquals(DhunResult.Success(timed), repo.getLyrics(track))
        assertEquals(0, candidates.fetchCalls)
        assertEquals(0, failed.calls)
        val result = repo.searchMatches(track, "manual query")
        assertEquals(4, result.matches.size)
        assertEquals(listOf("kugou"), result.failedProviders)
        assertFalse(result.matches.first().metadataVerified)
        assertTrue(result.matches.any { it.id == "alternate" })
    }

    @Test fun providerTimeoutFallsBackAndParentCancellationPropagates() = runTest {
        val slow = Source { delay(10_000); DhunResult.Success(timed) }
        val fallback = Source { DhunResult.Success(Lyrics.Unsynced("fallback")) }
        val repo = LyricsRepository(Cache(), slow, fallback, store("ytm", "lrclib"), providerTimeoutMs = 100)
        assertEquals(DhunResult.Success(Lyrics.Unsynced("fallback")), repo.getLyrics(track))
        assertEquals(100L, testScheduler.currentTime)
        val pending = async { repo.searchMatches(track, "query") }
        runCurrent()
        pending.cancel()
        assertFailsWith<CancellationException> { pending.await() }
    }

    @Test fun cancellationFromCacheReadOrWriteIsNeverSwallowed() = runTest {
        val cache = Cache()
        val source = Source { DhunResult.Success(timed) }
        val repo = LyricsRepository(cache, source, source)
        cache.cancelRead = true
        assertFailsWith<CancellationException> { repo.getLyrics(track) }
        assertEquals(0, source.calls)
        cache.cancelRead = false
        cache.cancelWrite = true
        assertFailsWith<CancellationException> { repo.getLyrics(track) }
    }

    @Test fun corruptChoiceIsIgnoredAndOversizeOrUnknownChoicesAreRejected() = runTest {
        val settings = store("ytm")
        settings.putString(SettingsKeys.lyricsMatchKey(track.id), "{invalid json")
        val source = Source { DhunResult.Success(timed) }
        val repo = LyricsRepository(Cache(), source, source, settings, source)
        assertEquals(DhunResult.Success(timed), repo.getLyrics(track))
        assertFailsWith<IllegalArgumentException> { repo.selectMatch(track.id, match.copy(provider = "unknown")) }
        assertFailsWith<IllegalArgumentException> {
            repo.selectMatch(track.id, match.copy(lyrics = Lyrics.Unsynced("x".repeat(600_000))))
        }
        assertNull(LyricsMatchCodec.decode("{\"version\":100}"))
    }

    @Test fun trackChangesCancelPickerAndRejectStaleSelections() = runTest {
        var cancelled = false
        val candidates = Candidates { _, _ ->
            try { delay(1_000); DhunResult.Success(listOf(match)) }
            finally { cancelled = true }
        }
        val settings = store("lrclib_search")
        val source = emptySource()
        val repo = LyricsRepository(Cache(), source, source, settings, candidates, providerTimeoutMs = 2_000)
        val refreshed = mutableListOf<String>()
        val controller = LyricsMatchController(repo, backgroundScope) { refreshed += it }
        controller.setTrack(track)
        runCurrent()
        controller.search(track.id, "first")
        runCurrent()
        controller.setTrack(track.copy(id = "song-b"))
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(cancelled)
        assertEquals("song-b", controller.state.value.trackId)
        assertTrue(controller.state.value.matches.isEmpty())
        controller.choose(track.id, match)
        runCurrent()
        assertTrue(refreshed.isEmpty())
        assertNull(repo.selectedMatch(track.id))
        controller.setTrack(null)
        assertNull(controller.state.value.trackId)
    }

    @Test fun controllerSavesRetriesResetsAndRecoversFromFailure() = runTest {
        var failing = true
        val candidates = Candidates { _, _ ->
            if (failing) DhunResult.Failure(DhunError.Network()) else DhunResult.Success(listOf(match))
        }
        val source = emptySource()
        val repo = LyricsRepository(Cache(), source, source, store("lrclib_search"), candidates)
        val refreshed = mutableListOf<String>()
        val controller = LyricsMatchController(repo, backgroundScope) { refreshed += it }
        controller.setTrack(track)
        runCurrent()
        controller.search(track.id, "query")
        runCurrent()
        assertFalse(controller.state.value.loading)
        assertEquals(listOf("lrclib_search"), controller.state.value.failedProviders)
        failing = false
        controller.search(track.id, "retry")
        runCurrent()
        controller.choose(track.id, match)
        runCurrent()
        assertEquals(match, repo.selectedMatch(track.id))
        assertEquals(match, controller.state.value.selected)
        assertEquals(listOf(track.id), refreshed)
        controller.choose(track.id, null)
        runCurrent()
        assertNull(repo.selectedMatch(track.id))
        assertEquals(2, refreshed.size)
    }

    @Test fun manualSearchKeepsDifferentMatchesAndHandlesJsonNullWithoutLiteralNullLyrics() = runTest {
        var requestQuery: String? = null
        val client = HttpClient(MockEngine {
            requestQuery = it.url.parameters["q"]
            respond("""[
              {"id":1,"trackName":"Song","artistName":"Artist","duration":240,"syncedLyrics":null,"plainLyrics":null},
              {"id":2,"trackName":"Other song","artistName":"Another artist","duration":180,"plainLyrics":"Preview"},
              {"id":3,"trackName":"Song","artistName":"Artist","duration":239,"syncedLyrics":"[00:01]<00:01>Word<00:02>","language":"en"}
            ]""")
        }) { install(HttpTimeout) }
        try {
            val source = LrcLibSearchSource(client)
            val matches = (source.candidates(track, "custom search") as DhunResult.Success).value
            assertEquals("custom search", requestQuery)
            assertEquals(2, matches.size)
            assertEquals("Other song", matches.first().title)
            assertEquals("en", matches.last().language)
            assertTrue(matches.last().synced)
            val automatic = (source.fetch(track) as DhunResult.Success).value as Lyrics.Synced
            assertEquals("Word", automatic.lines.single().text)
        } finally { client.close() }
    }
}
