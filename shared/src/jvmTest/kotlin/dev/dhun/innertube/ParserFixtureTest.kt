package dev.dhun.innertube

import dev.dhun.core.Album
import dev.dhun.core.Artist
import dev.dhun.core.HomeItem
import dev.dhun.core.Lyrics
import dev.dhun.core.Playlist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Parser tests against fixtures captured from LIVE responses (no network in
 * CI). Fixtures live in src/jvmTest/resources — copies of /tests/fixtures.
 */
class ParserFixtureTest {

    private fun fixture(name: String): String =
        javaClass.classLoader.getResourceAsStream("fixtures/$name")!!
            .bufferedReader().readText()

    private fun obj(json: String) =
        kotlinx.serialization.json.Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject

    @Test
    fun parsesLiveSearchSongsFixture() {
        val results = parseSearchResults("bohemian rhapsody", obj(fixture("search-songs-bohemian-rhapsody.json")))
        assertEquals(20, results.songs.size)
        val top = results.songs.first()
        assertEquals("Bohemian Rhapsody", top.title)
        assertEquals("utwMHfDZ6SA", top.id)
        assertEquals("Queen", top.artistName)
        assertTrue(top.albumName?.startsWith("A Night At The Opera") == true, "album: ${top.albumName}")
        assertEquals(355, top.durationSeconds) // 5:55
        assertTrue(top.thumbnailUrl?.startsWith("https") == true)
    }

    @Test
    fun parsesLiveRadioFixture() {
        val tracks = parseRelatedTracks(obj(fixture("next-radio-utwMHfDZ6SA.json")))
        assertEquals(50, tracks.size)
        assertEquals("Bohemian Rhapsody", tracks[0].title)
        assertEquals("Queen", tracks[0].artistName)
        assertTrue(tracks.all { it.id.isNotBlank() && it.title.isNotBlank() })
    }

    @Test
    fun parsesHomeBrowseFixture() {
        val sections = parseHomeSections(obj(fixture("browse-home.json")))
        assertEquals(2, sections.size)

        // Section 1: Quick picks (responsive items)
        val quickPicks = sections[0]
        assertEquals("Quick picks", quickPicks.title)
        assertEquals("Start radio with songs you like", quickPicks.subtitle)
        assertEquals(2, quickPicks.items.size)
        val track1 = (quickPicks.items[0] as HomeItem.TrackItem).track
        assertEquals("Starboy", track1.title)
        assertEquals("34Na4j8AVgA", track1.id)
        assertEquals("The Weeknd", track1.artistName)
        assertEquals(230, track1.durationSeconds) // 3:50

        // Section 2: Recommended albums / items (two-row items)
        val section2 = sections[1]
        assertEquals("Recommended albums", section2.title)
        assertEquals(3, section2.items.size)
        val album = (section2.items[0] as HomeItem.AlbumItem).album
        assertEquals("After Hours", album.title)
        assertEquals("MPREb_album_after_hours", album.id)

        val playlist = (section2.items[1] as HomeItem.PlaylistItem).playlist
        assertEquals("Today's Hits", playlist.title)
        assertEquals("VLPL_todays_hits", playlist.id)

        val artist = (section2.items[2] as HomeItem.ArtistItem).artist
        assertEquals("Dua Lipa", artist.name)
        assertEquals("UC_dua_lipa", artist.id)
    }

    @Test
    fun parsesSuggestionsShape() {
        val json = """
            {"contents":[{"searchSuggestionRenderer":{"suggestion":{"runs":[{"text":"yellow"},{"bold":"true","text":" coldplay"}]}}},
                         {"searchSuggestionRenderer":{"suggestion":{"runs":[{"text":"yellow coldplay live"}]}}}]}
        """.trimIndent()
        val suggestions = parseSuggestions(obj(json))
        assertEquals(listOf("yellow coldplay", "yellow coldplay live"), suggestions)
    }

    @Test
    fun parsesLyricsBrowseIdByPrefix() {
        val json = """{"a":{"b":"MPLYt_Lx8Kc3vT"},"c":{"d":"RDAMVMxyz"}}"""
        assertEquals("MPLYt_Lx8Kc3vT", parseLyricsBrowseId(obj(json)))
    }

    @Test
    fun parsesLyricsShelf() {
        val json = """
            {"frameworkUpdates":{},"contents":{"sectionListRenderer":{"contents":[
              {"musicDescriptionShelfRenderer":{"description":{"runs":[{"text":"Is this the real life?\nIs this just fantasy?"}]}}}
            ]}}}
        """.trimIndent()
        val lyrics = parseLyricsBrowse(obj(json))
        assertTrue(lyrics is Lyrics.Unsynced)
        assertTrue((lyrics as Lyrics.Unsynced).text.startsWith("Is this the real life?"))
    }

    @Test
    fun missingLyricsShelfIsNotAvailable() {
        val lyrics = parseLyricsBrowse(obj("{\"contents\":{}}"))
        assertEquals(Lyrics.NotAvailable, lyrics)
    }

    @Test
    fun parsesContinuationToken() {
        val json = """
            {"contents":{"sectionListRenderer":{"contents":[],"continuations":[{"nextContinuationData":{"continuation":"4smCG...=="}}]}}}
        """.trimIndent()
        val token = parseContinuationToken(obj(json))
        assertEquals("4smCG...==", token)
    }

/* ---------------- Phase 09 browse fixtures ------------------------------- */

    @Test
    fun parsesArtistPageFixture() {
        val page = parseArtistPage(obj(fixture("browse-artist-queen.json")), "UC_queen")
        assertEquals("Queen", page.artist.name)
        assertEquals("UC_queen", page.artist.id)
        assertEquals("48.5M monthly listeners", page.monthlyListeners)
        assertTrue(page.artist.thumbnailUrl?.contains("queen_1440") == true, "big thumb: ${page.artist.thumbnailUrl}")

        assertEquals(3, page.topSongs.size)
        assertEquals("Bohemian Rhapsody", page.topSongs[0].title)
        assertEquals("fJ9rUzIMcZQ", page.topSongs[0].id)
        assertEquals("Queen", page.topSongs[0].artistName)
        assertEquals("UC_queen", page.topSongs[0].artistId)
        assertEquals("MPREb_anato", page.topSongs[0].albumId)
        assertEquals(355, page.topSongs[0].durationSeconds)
        assertEquals("VLPL_queen_top_songs", page.topSongsPlaylistId)

        assertEquals(2, page.albums.size)
        assertEquals("A Night at the Opera", page.albums[0].title)
        assertEquals("MPREb_anato", page.albums[0].id)
        assertEquals(1, page.singles.size)
        assertEquals(1, page.featuredPlaylists.size)
        assertEquals("VLPL_classic_rock", page.featuredPlaylists[0].id)
        assertEquals(2, page.relatedArtists.size)
        assertEquals("UC_ledzeppelin", page.relatedArtists[0].id)

        assertTrue(page.description?.contains("British rock band") == true)
    }

    @Test
    fun parsesAlbumPageFixture() {
        val album = parseAlbumPage(obj(fixture("browse-album-anato.json")), "MPREb_anato")
        assertEquals("MPREb_anato", album.id)
        assertEquals("A Night at the Opera (Deluxe Remastered Version)", album.title)
        assertEquals("Queen", album.artistName)
        assertEquals("UC_queen", album.artistId)
        assertEquals("1975", album.year)
        assertEquals("12 songs", album.trackCountText)
        assertEquals("43 minutes", album.durationText)
        assertTrue(album.thumbnailUrl?.contains("anato_544") == true)
        assertTrue(album.description?.contains("fourth studio album") == true)

        // ordered track list
        assertEquals(6, album.tracks.size)
        assertEquals("Death on Two Legs", album.tracks[0].title)
        assertEquals("dRRtKqO5UpY", album.tracks[0].id)
        assertEquals(223, album.tracks[0].durationSeconds) // 3:43
        assertEquals("Bohemian Rhapsody (Remastered)", album.tracks[5].title)
        assertEquals(355, album.tracks[5].durationSeconds)
        assertTrue(album.tracks.all { it.artistName == "Queen" })
    }

    @Test
    fun parsesPlaylistPageFixture() {
        val playlist = parsePlaylistPage(obj(fixture("browse-playlist-todays-hits.json")), "VLPL_todays_hits")
        assertEquals("VLPL_todays_hits", playlist.id)
        assertEquals("Today's Hits", playlist.title)
        assertEquals("YouTube Music", playlist.authorName)
        assertEquals("50 songs", playlist.trackCountText)
        assertTrue(playlist.description?.contains("hottest tracks") == true)
        assertTrue(playlist.thumbnailUrl?.contains("todays_hits_544") == true)

        assertEquals(5, playlist.tracks.size)
        assertEquals("Espresso", playlist.tracks[0].title)
        assertEquals("eVli-tstMns", playlist.tracks[0].id)
        assertEquals("Mr. Brightside", playlist.tracks[4].title)
        assertEquals(223, playlist.tracks[4].durationSeconds) // 3:43
        assertTrue(playlist.tracks.all { it.artistName.isNotBlank() })
    }

}
