package dev.dhun.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExplicitBadgeTest {
    private fun row(json: String) = Json.parseToJsonElement(json) as JsonObject

    @Test
    fun searchParserPropagatesTheExplicitBadgeToTheTrack() {
        val root = row("""{
            "musicResponsiveListItemRenderer": {
                "playlistItemData": {"videoId": "song-1"},
                "flexColumns": [
                    {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Track"}]}}},
                    {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Singer"}]}}}
                ],
                "badges": [{"musicInlineBadgeRenderer":{"icon":{"iconType":"MUSIC_EXPLICIT_BADGE"}}}]
            }
        }""")
        val result = parseSearchResults("Track", root)
        assertTrue(result.songs.single().explicit)
    }

    @Test
    fun onlyTrackBadgesFlagExplicitContent() {
        assertTrue(row("""{"badges":[{"musicInlineBadgeRenderer":{"icon":{"iconType":"MUSIC_EXPLICIT_BADGE"}}}]}""").hasExplicitBadge())
        assertFalse(row("""{"title":"Explicit","menu":{"icon":{"iconType":"MUSIC_EXPLICIT_BADGE"}}}""").hasExplicitBadge())
        assertFalse(row("""{"badges":[{"musicInlineBadgeRenderer":{"icon":{"iconType":"MUSIC_PREMIUM_BADGE"}}}]}""").hasExplicitBadge())
    }
}
