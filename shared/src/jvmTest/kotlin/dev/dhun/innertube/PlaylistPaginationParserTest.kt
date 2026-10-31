package dev.dhun.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaylistPaginationParserTest {
    private fun obj(source: String) = Json.parseToJsonElement(source) as JsonObject

    private fun row(id: String, title: String) =
        """{"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"$id"},"flexColumns":[""" +
            """{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$title"}]}}},""" +
            """{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Artist"}]}}}]}}"""

    @Test
    fun initialPlaylistKeepsCoverCursorAndSparseRowArtwork() {
        val root = obj(
            """{"header":{"musicDetailHeaderRenderer":{""" +
                """"title":{"runs":[{"text":"Real playlist"}]},""" +
                """"subtitle":{"runs":[{"text":"Real artist"}]},""" +
                """"thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[""" +
                """{"url":"https://lh3.googleusercontent.com/playlist=w226-h226-l90-rj","width":226,"height":226}]}}}}},""" +
                """"contents":{"musicPlaylistShelfRenderer":{"continuations":[""" +
                """{"nextContinuationData":{"continuation":"next-playlist"}}],"contents":[${row("aaaaaaaaaaa", "First")}]}}}""",
        )
        val page = parsePlaylistPage(root, "VLreal")
        assertEquals("next-playlist", page.continuationToken)
        assertEquals(listOf("aaaaaaaaaaa"), page.tracks.map { it.id })
        assertEquals(page.thumbnailUrl, page.tracks.single().thumbnailUrl)
        assertEquals("Real artist", page.tracks.single().artistName)
    }

    @Test
    fun continuationWrapperReturnsOnlyItsRowsAndOwnCursor() {
        val root = obj(
            """{"continuationContents":{"musicPlaylistShelfContinuation":{"contents":[""" +
                row("bbbbbbbbbbb", "Second") + "," +
                """{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"last-page"}}}}]}}}""",
        )
        val page = parsePlaylistTracksPage(root)
        assertEquals(listOf("bbbbbbbbbbb"), page.tracks.map { it.id })
        assertEquals("last-page", page.continuationToken)
    }
}
