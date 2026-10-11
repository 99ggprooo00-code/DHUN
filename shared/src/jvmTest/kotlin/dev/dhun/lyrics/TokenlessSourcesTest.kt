package dev.dhun.lyrics

import dev.dhun.core.DhunResult
import dev.dhun.core.Lyrics
import dev.dhun.core.Track
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenlessSourcesTest {
    private val track = Track("video", "Song", "Artist", durationSeconds = 240)

    @Test
    fun searchFallbackPicksClosestDurationAndRejectsWrongSong(): Unit = runBlocking {
        val client = HttpClient(MockEngine {
            respond("""[
                {"trackName":"Song","artistName":"Artist","duration":210,"plainLyrics":"Wrong duration"},
                {"trackName":"Song","artistName":"Artist","duration":239,"syncedLyrics":"[00:01.00]Right"},
                {"trackName":"Different","artistName":"Artist","duration":240,"plainLyrics":"Wrong song"}
            ]""")
        }) { install(HttpTimeout) }
        try {
            val result = LrcLibSearchSource(client).fetch(track)
            assertTrue(result is DhunResult.Success && result.value is Lyrics.Synced)
            assertEquals("Right", (result.value as Lyrics.Synced).lines.single().text)
        } finally { client.close() }
    }

    @Test
    fun kugouDecodesLrcAndFailsOpenOnMissingCandidates(): Unit = runBlocking {
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/search" -> respond("""{"candidates":[{"id":"17","accesskey":"public","duration":240000}]}""")
                "/download" -> respond("""{"fmt":"lrc","content":"WzAwOjAxLjAwXUhlbGxv"}""")
                else -> respond("", HttpStatusCode.NotFound)
            }
        }) { install(HttpTimeout) }
        try {
            val result = KuGouLyricsSource(client).fetch(track)
            assertTrue(result is DhunResult.Success && result.value is Lyrics.Synced)
            assertEquals("Hello", (result.value as Lyrics.Synced).lines.single().text)
        } finally { client.close() }
    }
}
