package dev.dhun.innertube

import dev.dhun.core.DhunError
import dev.dhun.core.DhunException
import dev.dhun.core.detailString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Synthetic, strictly validated JSON fixtures; not a claim of live pagination success. */
class HomeFeedParserTest {
    private fun fixtureText(name: String): String = javaClass.classLoader
        .getResourceAsStream("fixtures/home-pagination/$name.json")!!
        .bufferedReader().use { it.readText() }

    private fun fixture(name: String): JsonObject = json(fixtureText(name))

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject

    @Test
    fun usesFeedTokenInsteadOfTheFirstNestedShelfToken() {
        val root = fixture("initial-feed-and-shelf-token")
        assertEquals("shelf-only", parseContinuationToken(root)) // reproduces the old recursive lookup
        val parsed = parseHomeFeedPage(root)
        assertEquals("page-two", parsed.continuationToken)
        assertEquals(listOf("a"), parsed.sections.single().tracks.map { it.id })
    }

    @Test
    fun nestedShelfTokenIsNotAFeedContinuation() {
        assertNull(parseHomeFeedPage(fixture("shelf-token-only")).continuationToken)
    }

    @Test
    fun supportsAListLevelContinuationItem() {
        assertEquals("page-two", parseHomeFeedPage(fixture("initial-command-token")).continuationToken)
    }

    @Test
    fun parsesSectionListContinuationAndItsNextToken() {
        val result = parseHomeFeedPage(fixture("section-list-continuation"))
        assertEquals("b", result.sections.single().tracks.single().id)
        assertEquals("page-three", result.continuationToken)
    }

    @Test
    fun parsesMusicShelfContinuationAndItsNextToken() {
        val result = parseHomeFeedPage(fixture("music-shelf-continuation"))
        assertEquals("b", result.sections.single().tracks.single().id)
        assertEquals("page-three", result.continuationToken)
    }

    @Test
    fun acceptsMusicShelfRendererInAppendActions() {
        val result = parseHomeFeedPage(fixture("music-shelf-action"))
        assertEquals("b", result.sections.single().tracks.single().id)
        assertEquals("page-three", result.continuationToken)
    }

    @Test
    fun acceptsAContinuationShelfDirectlyUnderContents() {
        val result = parseHomeFeedPage(fixture("direct-shelf-contents"))
        assertEquals("b", result.sections.single().tracks.single().id)
        assertEquals("page-three", result.continuationToken)
    }

    @Test
    fun acceptsSectionEntriesNestedDirectlyInABrowseRenderer() {
        val result = parseHomeFeedPage(fixture("nested-browse-contents"))
        assertEquals("c", result.sections.single().tracks.single().id)
        assertEquals("page-four", result.continuationToken)
    }

    @Test
    fun parsesAppendActionsInAllSupportedResponseFields() {
        val actions = fixture("append-action").arr("onResponseReceivedActions")!!
        for (field in listOf("onResponseReceivedActions", "onResponseReceivedEndpoints", "onResponseReceivedCommands")) {
            val result = parseHomeFeedPage(JsonObject(mapOf(field to actions)))
            assertEquals("b", result.sections.single().tracks.single().id)
            assertEquals("page-three", result.continuationToken)
        }
    }

    @Test
    fun honoursTheSelectedDesktopTab() {
        val result = parseHomeFeedPage(fixture("selected-desktop-tab"))
        assertEquals("right", result.sections.single().tracks.single().id)
        assertNull(result.continuationToken)
    }

    @Test
    fun supportsLegacyDirectListsAndButtonContinuations() {
        val list = fixture("button-command-token")
        assertEquals("next", parseHomeFeedPage(list).continuationToken)
        assertEquals("next", parseHomeFeedPage(JsonObject(mapOf("contents" to list))).continuationToken)
    }

    @Test
    fun unexpectedContinuationShapeIsAnErrorNotSilentExhaustion() {
        val error = assertFailsWith<DhunException> {
            parseHomeFeedPage(fixture("wrong-shelf-continuation"))
        }
        assertTrue(error.error.toString().contains("shape=top"))
    }

    @Test
    fun unrelatedActionsCannotOverrideAFullPagesToken() {
        val result = parseHomeFeedPage(fixture("initial-feed-with-unrelated-actions"))
        assertEquals("a", result.sections.single().tracks.single().id)
        assertEquals("page-two", result.continuationToken)
    }

    @Test
    fun doesNotFlattenHorizontalAndVerticalContinuationTargetsTogether() {
        val result = parseHomeFeedPage(fixture("mixed-feed-and-shelf-actions"))
        assertEquals("b", result.sections.single().tracks.single().id)
        assertEquals("feed-next", result.continuationToken)
    }

    @Test
    fun acceptsSplitCommandsOnlyForTheSameNamedTarget() {
        val result = parseHomeFeedPage(fixture("split-feed-actions"))
        assertEquals("b", result.sections.single().tracks.single().id)
        assertEquals("page-three", result.continuationToken)
    }

    @Test
    fun ambiguousTargetsProduceARetryableErrorNotAnArbitraryCursor() {
        for (name in listOf("ambiguous-feed-actions", "ambiguous-empty-actions", "anonymous-feed-actions")) {
            val error = assertFailsWith<DhunException>(name) { parseHomeFeedPage(fixture(name)) }
            assertTrue(error.error.toString().contains("ambiguous"), name)
        }
    }

    @Test
    fun emptyActionsCanAdvanceOrEndTheFeed() {
        val advancing = parseHomeFeedPage(fixture("empty-advancing-action"))
        assertTrue(advancing.sections.isEmpty())
        assertEquals("page-three", advancing.continuationToken)
        val exhausted = parseHomeFeedPage(fixture("exhausted-action"))
        assertTrue(exhausted.sections.isEmpty())
        assertNull(exhausted.continuationToken)
    }

    @Test
    fun supportsReloadCommands() {
        val result = parseHomeFeedPage(fixture("reload-feed-action"))
        assertEquals("b", result.sections.single().tracks.single().id)
        assertEquals("page-three", result.continuationToken)
    }

    @Test
    fun duplicateActionEnvelopesDoNotDuplicateShelves() {
        val actions = fixture("append-action").arr("onResponseReceivedActions")!!
        val result = parseHomeFeedPage(JsonObject(mapOf(
            "onResponseReceivedActions" to actions,
            "onResponseReceivedCommands" to actions,
        )))
        assertEquals(1, result.sections.size)
        assertEquals("page-three", result.continuationToken)
    }

    @Test
    fun acceptsMusicTabRendererWithoutConfusingItWithAnUnknownTab() {
        // Same strictly validated fixture, with only the envelope key changed.
        val root = json(fixtureText("initial-feed-and-shelf-token").replace("\"tabRenderer\"", "\"musicTabRenderer\""))
        val parsed = parseHomeFeedPage(root)
        assertEquals("a", parsed.sections.single().tracks.single().id)
        assertEquals("page-two", parsed.continuationToken)
    }

    @Test
    fun validEmptySectionListIsAnEmptyPageNotMalformedJson() {
        val parsed = parseHomeFeedPage(json(
            """{"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"musicTabRenderer":{
               "selected":true,"content":{"sectionListRenderer":{"contents":[]}}}]}}}""",
        ))
        assertTrue(parsed.sections.isEmpty())
        assertNull(parsed.continuationToken)
    }

    @Test
    fun emptyEnvelopeReportsAnActionableParseError() {
        val error = assertFailsWith<DhunException> { parseHomeFeedPage(json("{}")) }.error
        assertTrue(error is DhunError.Parse)
        assertTrue(error.detailString().orEmpty().contains("no section list"))
        assertTrue(error.detailString().orEmpty().contains("shape=top"))
    }

    @Test
    fun missingOptionalFieldsAndUnknownKeysKeepAValidTrack() {
        val parsed = parseHomeFeedPage(fixture("resilient-sections"))
        val track = parsed.sections.single().tracks.single()
        assertEquals("survives", track.id)
        assertEquals("Unknown artist", track.artistName)
        assertNull(track.thumbnailUrl)
        assertNull(track.durationSeconds)
    }

    @Test
    fun unknownAndBrokenSectionsDoNotDiscardTheValidShelf() {
        val parsed = parseHomeFeedPage(fixture("resilient-sections"))
        assertEquals(1, parsed.sections.size)
        assertEquals("Valid shelf", parsed.sections.single().title)
        assertEquals(listOf("survives"), parsed.sections.single().tracks.map { it.id })
        assertNull(parsed.continuationToken)
    }
}
