package dev.dhun.innertube

import dev.dhun.core.Lyrics
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
