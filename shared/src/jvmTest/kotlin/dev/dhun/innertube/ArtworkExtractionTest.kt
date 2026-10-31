package dev.dhun.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtworkExtractionTest {
    private fun obj(source: String): JsonObject = Json.parseToJsonElement(source) as JsonObject

    @Test
    fun alternateRendererAndUnorderedSizesUseTheLargestRealThumbnail() {
        val item = obj(
            """{"thumbnail":{"croppedSquareThumbnailRenderer":{"thumbnail":{"thumbnails":[""" +
                """{"url":"https://lh3.googleusercontent.com/cover=w226-h226-l90-rj","width":226,"height":226},""" +
                """{"url":"https://lh3.googleusercontent.com/cover=w60-h60-l90-rj","width":60,"height":60}]}}}}""",
        )
        assertEquals(
            "https://lh3.googleusercontent.com/cover=w544-h544-l90-rj",
            thumbnailOf(item),
        )
    }

    @Test
    fun unknownNestedRendererFallsBackToTheNearestThumbnailArray() {
        val item = obj(
            """{"newHeaderRenderer":{"cover":{"thumbnails":[""" +
                """{"url":"//yt3.ggpht.com/avatar=s176-c-k-c0x00ffffff-no-rj","width":176,"height":176}]}}}""",
        )
        assertEquals(
            "https://yt3.ggpht.com/avatar=s544-c-k-c0x00ffffff-no-rj",
            thumbnailOf(item),
        )
        assertNull(thumbnailOf(obj("""{"newHeaderRenderer":{"title":{"text":"No image"}}}""")))
    }

    @Test
    fun searchResultsUseTheSameAlternateThumbnailExtraction() {
        val root = obj(
            """{"contents":[{"musicResponsiveListItemRenderer":{""" +
                """"playlistItemData":{"videoId":"utwMHfDZ6SA"},""" +
                """"thumbnail":{"futureRenderer":{"thumbnails":[{"url":"https://lh3.googleusercontent.com/song=w120-h120-l90-rj","width":120,"height":120}]}},""" +
                """"flexColumns":[""" +
                """{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Real song"}]}}},""" +
                """{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Real artist"}]}}}""" +
                """]}}]}""",
        )
        val result = parseSearchResults("real", root)
        assertEquals(1, result.songs.size)
        assertEquals(
            "https://lh3.googleusercontent.com/song=w544-h544-l90-rj",
            result.songs.single().thumbnailUrl,
        )
    }
}
