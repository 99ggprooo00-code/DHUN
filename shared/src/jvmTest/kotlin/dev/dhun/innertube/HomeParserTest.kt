package dev.dhun.innertube

import dev.dhun.core.HomeItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 07 parser tests for home sections and search continuation.
 */
class HomeParserTest {

    private fun obj(json: String) =
        kotlinx.serialization.json.Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject

    @Test
    fun parseHomeSections_withTwoRowItemAlbums() {
        val json = """
        {
          "contents": [{
            "sectionListRenderer": {
              "contents": [{
                "musicShelfRenderer": {
                  "title": {"runs": [{"text": "Recently Played"}]},
                  "contents": [{
                    "musicTwoRowItemRenderer": {
                      "title": {"runs": [{"text": "Greatest Hits"}]},
                      "subtitle": {"runs": [{"text": "Queen"}]},
                      "thumbnail": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [{"url": "https://example.com/album.jpg"}]}}},
                      "navigationEndpoint": {"browseEndpoint": {"browseId": "MPREb_ALBUM123"}}
                    }
                  }]
                }
              }]
            }
          }]
        }
        """.trimIndent()

        val sections = parseHomeSections(obj(json))
        assertEquals(1, sections.size)
        assertEquals("Recently Played", sections[0].title)
        assertEquals(1, sections[0].items.size)
        val item = sections[0].items[0]
        assertTrue(item is HomeItem.AlbumItem)
        assertEquals("Greatest Hits", item.album.title)
        assertEquals("Queen", item.album.artistName)
        assertEquals("MPREb_ALBUM123", item.album.id)
    }

    @Test
    fun parseHomeSections_withTwoRowItemArtists() {
        val json = """
        {
          "contents": [{
            "sectionListRenderer": {
              "contents": [{
                "musicShelfRenderer": {
                  "title": {"runs": [{"text": "Top Artists"}]},
                  "contents": [{
                    "musicTwoRowItemRenderer": {
                      "title": {"runs": [{"text": "Queen"}]},
                      "subtitle": {"runs": [{"text": "1.2M subscribers"}]},
                      "navigationEndpoint": {"browseEndpoint": {"browseId": "UCbC7xZJhLQ7"}}
                    }
                  }]
                }
              }]
            }
          }]
        }
        """.trimIndent()

        val sections = parseHomeSections(obj(json))
        assertEquals(1, sections.size)
        val item = sections[0].items[0]
        assertTrue(item is HomeItem.ArtistItem)
        assertEquals("Queen", item.artist.name)
        assertEquals("UCbC7xZJhLQ7", item.artist.id)
    }

    @Test
    fun parseHomeSections_withTwoRowItemPlaylists() {
        val json = """
        {
          "contents": [{
            "sectionListRenderer": {
              "contents": [{
                "musicShelfRenderer": {
                  "title": {"runs": [{"text": "Your Mixes"}]},
                  "contents": [{
                    "musicTwoRowItemRenderer": {
                      "title": {"runs": [{"text": "Chill Mix 1"}]},
                      "subtitle": {"runs": [{"text": "Playlist • 50 songs"}]},
                      "navigationEndpoint": {"browseEndpoint": {"browseId": "VLPL_ID123"}}
                    }
                  }]
                }
              }]
            }
          }]
        }
        """.trimIndent()

        val sections = parseHomeSections(obj(json))
        assertEquals(1, sections.size)
        val item = sections[0].items[0]
        assertTrue(item is HomeItem.PlaylistItem)
        assertEquals("Chill Mix 1", item.playlist.title)
        assertEquals("VLPL_ID123", item.playlist.id)
    }

    @Test
    fun parseHomeSections_withTwoRowItemTracks() {
        val json = """
        {
          "contents": [{
            "sectionListRenderer": {
              "contents": [{
                "musicShelfRenderer": {
                  "title": {"runs": [{"text": "Because You Like"}]},
                  "contents": [{
                    "musicTwoRowItemRenderer": {
                      "title": {"runs": [{"text": "Bohemian Rhapsody"}]},
                      "subtitle": {"runs": [{"text": "Queen"}]},
                      "navigationEndpoint": {"watchEndpoint": {"videoId": "fJ9rUzIMcZQ"}}
                    }
                  }]
                }
              }]
            }
          }]
        }
        """.trimIndent()

        val sections = parseHomeSections(obj(json))
        assertEquals(1, sections.size)
        val item = sections[0].items[0]
        assertTrue(item is HomeItem.TrackItem)
        assertEquals("fJ9rUzIMcZQ", item.track.id)
        assertEquals("Bohemian Rhapsody", item.track.title)
    }

    @Test
    fun parseHomeSections_skipsEmptyShelves() {
        val json = """
        {
          "contents": [{
            "sectionListRenderer": {
              "contents": [{
                "musicShelfRenderer": {
                  "title": {"runs": [{"text": "Empty Section"}]},
                  "contents": []
                }
              }]
            }
          }]
        }
        """.trimIndent()

        val sections = parseHomeSections(obj(json))
        assertTrue(sections.isEmpty())
    }

    @Test
    fun parseHomeSections_skipsListenAgainBanners() {
        val json = """
        {
          "contents": [{
            "sectionListRenderer": {
              "contents": [{
                "musicShelfRenderer": {
                  "title": {"runs": [{"text": "Listen again"}]},
                  "contents": [{"musicTwoRowItemRenderer": {"title": {"runs": [{"text": "Skip"}]}}}]
                }
              }]
            }
          }]
        }
        """.trimIndent()

        val sections = parseHomeSections(obj(json))
        // "Listen again" sections are skipped
        assertTrue(sections.isEmpty() || sections.none { it.title == "Listen again" })
    }

    @Test
    fun parseSearchContinuation_extractsNextToken() {
        val json = """
        {
          "continuationContents": {
            "musicShelfContinuation": {
              "contents": [],
              "continuations": [
                {
                  "nextContinuationData": {
                    "continuation": "TOKEN_PAGE_2"
                  }
                }
              ]
            }
          }
        }
        """.trimIndent()

        val result = parseSearchContinuation(obj(json))
        assertEquals("TOKEN_PAGE_2", result.continuation)
    }

    @Test
    fun parseSearchContinuation_extractsSongs() {
        val json = """
        {
          "continuationContents": {
            "musicShelfContinuation": {
              "contents": [
                {
                  "musicResponsiveListItemRenderer": {
                    "playlistItemData": {"videoId": "vid1"},
                    "flexColumns": [
                      {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Song One"}]}}},
                      {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Artist One"}]}}}
                    ]
                  }
                }
              ],
              "continuations": []
            }
          }
        }
        """.trimIndent()

        val result = parseSearchContinuation(obj(json))
        assertEquals(1, result.songs.size)
        assertEquals("vid1", result.songs[0].id)
        assertEquals("Song One", result.songs[0].title)
        assertEquals("Artist One", result.songs[0].artistName)
        assertEquals(null, result.continuation)
    }

    @Test
    fun parseSearchContinuation_handlesEmptyContinuation() {
        val json = """{"continuationContents": {"musicShelfContinuation": {"contents": []}}}"""
        val result = parseSearchContinuation(obj(json))
        assertTrue(result.songs.isEmpty())
        assertTrue(result.continuation == null)
    }
}
