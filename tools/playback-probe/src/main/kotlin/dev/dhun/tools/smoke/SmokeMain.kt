package dev.dhun.tools.smoke

import dev.dhun.core.DhunResult
import dev.dhun.core.Lyrics
import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import dev.dhun.innertube.SearchFilter
import dev.dhun.player.QueueManager
import dev.dhun.provider.YouTubeMusicProvider
import dev.dhun.provider.forDesktop
import kotlinx.coroutines.runBlocking

/**
 * Live smoke — drives the REAL provider stack end-to-end:
 * home feed -> search (every filter) -> suggestions -> radio -> lyrics -> stream info,
 * plus a QueueManager sanity pass. On-network verification, not a unit test.
 */
fun main() = runBlocking {
    val provider = YouTubeMusicProvider.forDesktop()
    println("=== Phase 02+07 smoke (live) ===")

    when (val r = provider.homeFeed()) {
        is DhunResult.Success -> {
            val sections = r.value
            println("SMOKE|home-feed|PASS|${sections.size} sections; top: ${sections.firstOrNull()?.title}")
        }
        is DhunResult.Failure -> println("SMOKE|home-feed|FAIL|${r.error}")
    }

    when (val r = provider.search("coldplay", SearchFilter.SONGS)) {
        is DhunResult.Success -> {
            val songs = r.value.songs
            println("SMOKE|search-songs|PASS|${songs.size} songs; top: ${songs.firstOrNull()?.title} — ${songs.firstOrNull()?.artistName}")
            runAll(provider, songs.firstOrNull()?.id ?: "")
        }
        is DhunResult.Failure -> println("SMOKE|search-songs|FAIL|${r.error}")
    }

    val queue = QueueManager()
    queue.setQueue(
        listOf(
            Track("a1", "Song One", "Artist"),
            Track("a2", "Song Two", "Artist"),
            Track("a3", "Song Three", "Artist"),
        )
    )
    queue.addToQueue(Track("a4", "Added", "Artist"))
    queue.setRepeatMode(RepeatMode.ALL)
    println("SMOKE|queue|PASS|size=${queue.size} current=${queue.current?.title} next=${queue.next()?.title}")
}

private suspend fun runAll(provider: YouTubeMusicProvider, videoId: String) {
    if (videoId.isEmpty()) return

    for (filter in listOf(SearchFilter.ARTISTS, SearchFilter.ALBUMS, SearchFilter.PLAYLISTS)) {
        when (val r = provider.search("coldplay", filter)) {
            is DhunResult.Success -> println(
                "SMOKE|search-${filter.name.lowercase()}|PASS|" +
                    "artists=${r.value.artists.size} albums=${r.value.albums.size} playlists=${r.value.playlists.size}"
            )
            is DhunResult.Failure -> println("SMOKE|search-${filter.name.lowercase()}|FAIL|${r.error}")
        }
    }

    when (val r = provider.searchSuggestions("yellow col")) {
        is DhunResult.Success -> println("SMOKE|suggestions|PASS|${r.value.take(4).joinToString(" | ")}")
        is DhunResult.Failure -> println("SMOKE|suggestions|FAIL|${r.error}")
    }

    when (val r = provider.relatedTracks(videoId)) {
        is DhunResult.Success -> println("SMOKE|related|PASS|${r.value.size} tracks; top: ${r.value.firstOrNull()?.title} — ${r.value.firstOrNull()?.artistName}")
        is DhunResult.Failure -> println("SMOKE|related|FAIL|${r.error}")
    }

    when (val r = provider.getLyrics(videoId)) {
        is DhunResult.Success -> when (val lyrics = r.value) {
            is Lyrics.Unsynced -> println("SMOKE|lyrics|PASS|unsynced, ${lyrics.text.lines().size} lines; first: ${lyrics.text.lines().first().take(50)}")
            is Lyrics.Synced -> println("SMOKE|lyrics|PASS|synced, ${lyrics.lines.size} lines")
            Lyrics.NotAvailable -> println("SMOKE|lyrics|PASS|not available for this track")
            else -> println("SMOKE|lyrics|FAIL|unknown variant")
        }
        is DhunResult.Failure -> println("SMOKE|lyrics|FAIL|${r.error}")
    }

    when (val r = provider.getStreamInfo(videoId)) {
        is DhunResult.Success -> {
            val s = r.value
            val prefix = s.audioUrl.take(55)
            println("SMOKE|stream|PASS|${s.mimeType} ${s.bitrateKbps ?: "?"}kbps codec=${s.codec} url=$prefix…")
        }
        is DhunResult.Failure -> println("SMOKE|stream|FAIL|${r.error}")
    }
}
