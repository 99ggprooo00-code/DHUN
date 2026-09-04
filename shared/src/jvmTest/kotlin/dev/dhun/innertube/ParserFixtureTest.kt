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
}
