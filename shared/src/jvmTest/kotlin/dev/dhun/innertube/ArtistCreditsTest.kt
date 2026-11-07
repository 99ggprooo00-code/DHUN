package dev.dhun.innertube

import dev.dhun.core.ArtistCredit
import dev.dhun.core.Track
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Multi-artist credits.
 *
 * The defect these pin down: the Full Player made the whole `artistName`
 * string one clickable target pointing at `Track.artistId`, which is only the
 * first credited channel — so a collaboration silently opened one artist and
 * the other credits were unreachable text.
 */
class ArtistCreditsTest {

    private fun obj(json: String) = Json.parseToJsonElement(json) as JsonObject

    /** One byline run, linked to [id] (the shape YTM actually serves). */
    private fun run(name: String, id: String): String =
        """{"text":"$name","navigationEndpoint":{"browseEndpoint":{"browseId":"$id"}}}"""

    @Test
    fun everyLinkedCollaboratorBecomesItsOwnNavigableCredit() {
        val byline = obj("""{"runs":[${run("Arijit Singh", "UCarijit")},{"text":" & "},{"text":"Shreya Ghoshal","navigationEndpoint":{"browseEndpoint":{"browseId":"UCshreya"}}}]}""")
        val credits = ArtistCredits.resolve(
            displayName = "Arijit Singh & Shreya Ghoshal",
            linked = ArtistCredits.linkedCredits(byline),
            fallbackId = "UCarijit",
        )
        assertEquals(listOf("Arijit Singh", "Shreya Ghoshal"), credits.map { it.name })
        assertEquals(listOf("UCarijit", "UCshreya"), credits.map { it.id })
    }

    @Test
    fun duplicateLinksCollapseToSourceOrder() {
        val byline = obj("""{"runs":[${run("Queen", "UCqueen")},{"text":" • "},{"text":"Queen","navigationEndpoint":{"browseEndpoint":{"browseId":"UCqueen"}}}]}""")
        assertEquals(listOf("UCqueen"), ArtistCredits.linkedArtistIds(byline))
    }

    @Test
    fun nonArtistBrowseIdsAreNeverTreatedAsArtists() {
        // Albums (MPRE…) and playlists (VL…) appear in the same subtitle runs.
        val byline = obj("""{"runs":[${run("A", "UCa")},{"text":" • "},{"text":"Album","navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb123"}}},{"text":" • "},{"text":"List","navigationEndpoint":{"browseEndpoint":{"browseId":"VLPL1"}}}]}""")
        assertEquals(listOf("UCa"), ArtistCredits.linkedArtistIds(byline))
    }

    @Test
    fun aSingleLinkedNameForAMultiNameStringIsNotInventedIntoLinks() {
        // "A & B" where only A carries a channel: splitting it would create two
        // links to A's page — a worse bug than the one being fixed.
        val credits = ArtistCredits.resolve(
            displayName = "A & B",
            linked = listOf(ArtistCredit("A", "UCa")),
            fallbackId = "UCa",
        )
        assertEquals(1, credits.size)
        assertEquals("A & B", credits.single().name)
        assertEquals("UCa", credits.single().id)
    }

    @Test
    fun aBylineThatOmitsANamedArtistKeepsTheDisplayedStringWhole() {
        // Two links, but the visible credit names a third plain-text artist:
        // adopting the short linked list would silently drop that artist.
        val credits = ArtistCredits.resolve(
            displayName = "A, B & C",
            linked = listOf(ArtistCredit("A", "UCa"), ArtistCredit("B", "UCb")),
            fallbackId = "UCa",
        )
        assertEquals(listOf("A, B & C"), credits.map { it.name })
    }

    @Test
    fun namesSplitOnTheSeparatorsProvidersActuallyUse() {
        assertEquals(listOf("A", "B"), ArtistCredits.splitNames("A & B"))
        assertEquals(listOf("A", "B", "C"), ArtistCredits.splitNames("A, B, C"))
        assertEquals(listOf("A", "B"), ArtistCredits.splitNames("A feat. B"))
        assertEquals(listOf("A", "B"), ArtistCredits.splitNames("A ft. B"))
        assertEquals(listOf("A", "B"), ArtistCredits.splitNames("A featuring B"))
        assertEquals(listOf("A", "B"), ArtistCredits.splitNames("A x B"))
        // "&" is a co-artist separator, so it splits — the point is that the
        // list is ordered and lossless, not that any "&" is preserved.
        assertEquals(listOf("Simon", "Garfunkel"), ArtistCredits.splitNames("Simon & Garfunkel"))
        // A name with no separator is one credit.
        assertEquals(listOf("Arijit Singh"), ArtistCredits.splitNames("Arijit Singh"))
        // "ft"/"feat" only split as whole words: a real name containing them
        // must not be torn in half, and the abbreviation may or may not end
        // with a dot without leaving one glued to the next name.
        assertEquals(listOf("Daft Punk"), ArtistCredits.splitNames("Daft Punk"))
        assertEquals(listOf("A", "B"), ArtistCredits.splitNames("A feat B"))
        assertEquals(listOf("A", "B"), ArtistCredits.splitNames("A,B"))
        assertTrue(ArtistCredits.splitNames("   ").isEmpty())
    }

    @Test
    fun aTrackWithoutLinkedCreditsStillHasExactlyOneDisplayedCredit() {
        val plain = Track(id = "v", title = "T", artistName = "Solo", artistId = "UCsolo")
        assertEquals(listOf(ArtistCredit("Solo", "UCsolo")), plain.artistCredits)
        // No id: the name is still shown, it is simply not a link.
        val unknown = Track(id = "v", title = "T", artistName = "Unknown artist")
        assertEquals(listOf(ArtistCredit("Unknown artist", null)), unknown.artistCredits)
        assertNull(unknown.artistCredits.single().id)
    }

    @Test
    fun storedCreditsWinOverTheJoinedDisplayName() {
        val track = Track(
            id = "v",
            title = "T",
            artistName = "A & B",
            artistId = "UCa",
            artists = listOf(ArtistCredit("A", "UCa"), ArtistCredit("B", "UCb")),
        )
        assertEquals(listOf("UCa", "UCb"), track.artistCredits.map { it.id })
    }

    @Test
    fun blankRowsProduceNoCredits() {
        assertTrue(ArtistCredits.resolve("", emptyList(), "UCa").isEmpty())
        assertTrue(ArtistCredits.linkedCredits(null).isEmpty())
    }
}
