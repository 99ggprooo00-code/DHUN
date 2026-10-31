package dev.dhun.innertube

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class HomeHtmlParserTest {
    private fun json(text: String) = Json.parseToJsonElement(text) as JsonObject

    @Test
    fun extractsBalancedInitialDataWithBracesAndQuotesInsideStrings() {
        val payload = """{"contents":{"sectionListRenderer":{"contents":[],"title":"A } brace and a \" quote"}},"responseContext":{"visitorData":"not-displayed"}}"""
        val html = "<html><script>var ytInitialData = $payload;</script></html>"
        assertEquals(json(payload), parseHomeInitialData(html))
    }

    @Test
    fun acceptsWindowAssignmentAndMusicTabEnvelope() {
        val payload = """{"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"musicTabRenderer":{"content":{"sectionListRenderer":{"contents":[]}}}}]}}}"""
        assertEquals(json(payload), parseHomeInitialData("""<script>window["ytInitialData"] = $payload;</script>"""))
    }

    @Test
    fun rejectsUnrelatedMalformedOrIncompleteJson() {
        assertNull(parseHomeInitialData("""<script>var ytInitialData = {"contents":{"other":{}}};</script>"""))
        assertNull(parseHomeInitialData("""<script>var ytInitialData = {not-json};</script>"""))
        assertNull(parseHomeInitialData("""<script>var ytInitialData = {"contents":"""))
        assertNull(parseHomeInitialData(""))
    }
}
