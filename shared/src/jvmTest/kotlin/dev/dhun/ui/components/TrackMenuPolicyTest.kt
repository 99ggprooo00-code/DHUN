package dev.dhun.ui.components

import androidx.compose.ui.unit.dp
import dev.dhun.core.Track
import dev.dhun.design.DhunSpacing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the compact ⋮ menu: which actions it offers, in which order, with which
 * labels, and the geometry that makes it read as a *menu* rather than as the
 * old centered sheet (280–380dp wide, double padding, a big header, a divider,
 * ~48dp rows and its own Close button).
 *
 * Pure data on purpose — every overflow affordance in the app is mounted once
 * from `DhunAppShell`, so this one list is what the full player, the playlist /
 * album / artist pages, Home, Search and Library all open.
 */
class TrackMenuPolicyTest {

    private fun aTrack(
        artistName: String = "Queen",
        albumName: String? = "A Night at the Opera",
        albumId: String? = "MPREb_anato",
    ) = Track(
        id = "utwMHfDZ6SA",
        title = "Bohemian Rhapsody",
        artistName = artistName,
        albumName = albumName,
        albumId = albumId,
        thumbnailUrl = "https://lh3.googleusercontent.com/anato_544.jpg",
    )

    private fun actions(
        track: Track = aTrack(),
        canDownload: Boolean = true,
        canNavigateToArtist: Boolean = true,
        canNavigateToAlbum: Boolean = true,
    ): List<TrackMenuAction> = TrackMenuPolicy.actionsFor(
        track = track,
        canDownload = canDownload,
        canNavigateToArtist = canNavigateToArtist,
        canNavigateToAlbum = canNavigateToAlbum,
    )

    @Test
    fun offersEveryActionInTheAcceptedOrderWithShortLabels() {
        assertEquals(
            listOf(
                TrackMenuAction.PLAY_NEXT,
                TrackMenuAction.ADD_TO_QUEUE,
                TrackMenuAction.ADD_TO_PLAYLIST,
                TrackMenuAction.DOWNLOAD,
                TrackMenuAction.GO_TO_ARTIST,
                TrackMenuAction.GO_TO_ALBUM,
            ),
            actions(),
        )
        // The labels are what the compact menu is sized around: no
        // parenthetical target names ("Go to artist (Queen)") stretching a row.
        assertEquals(
            listOf(
                "Play next",
                "Add to queue",
                "Add to playlist",
                "Download",
                "Go to artist",
                "Go to album",
            ),
            TrackMenuAction.entries.map { it.label },
        )
        // Every row keeps a glyph — the old sheet had them too.
        assertTrue(TrackMenuAction.entries.all { it.icon.pathData.isNotBlank() })
    }

    @Test
    fun theThreeQueueActionsSurviveEveryCapabilityCombination() {
        val bare = actions(
            track = aTrack(artistName = "", albumName = null, albumId = null),
            canDownload = false,
            canNavigateToArtist = false,
            canNavigateToAlbum = false,
        )
        assertEquals(
            listOf(
                TrackMenuAction.PLAY_NEXT,
                TrackMenuAction.ADD_TO_QUEUE,
                TrackMenuAction.ADD_TO_PLAYLIST,
            ),
            bare,
        )
    }

    @Test
    fun downloadOnlyAppearsWhereTheHostSuppliesADownloadManager() {
        assertTrue(TrackMenuAction.DOWNLOAD in actions(canDownload = true))
        assertFalse(TrackMenuAction.DOWNLOAD in actions(canDownload = false))
    }

    @Test
    fun goToArtistNeedsAnArtistNameToNavigateOrSearchFor() {
        // The shell navigates by `artistId` and otherwise searches for the
        // name, so a name is the requirement — and a blank one must not offer
        // a row that would run an empty search.
        assertTrue(TrackMenuAction.GO_TO_ARTIST in actions(track = aTrack(artistName = "Queen")))
        assertFalse(TrackMenuAction.GO_TO_ARTIST in actions(track = aTrack(artistName = "")))
        assertFalse(TrackMenuAction.GO_TO_ARTIST in actions(track = aTrack(artistName = "   ")))
        assertFalse(
            TrackMenuAction.GO_TO_ARTIST in actions(canNavigateToArtist = false),
            "no callback, no row",
        )
    }

    @Test
    fun goToAlbumNeedsAnAlbumIdOrAnAlbumName() {
        assertTrue(TrackMenuAction.GO_TO_ALBUM in actions(track = aTrack()))
        // Name only: the shell falls back to an album search.
        assertTrue(
            TrackMenuAction.GO_TO_ALBUM in actions(track = aTrack(albumName = "A Night at the Opera", albumId = null)),
        )
        // Id only: browse straight to the page.
        assertTrue(TrackMenuAction.GO_TO_ALBUM in actions(track = aTrack(albumName = null, albumId = "MPREb_anato")))
        // Neither: nothing to open.
        assertFalse(TrackMenuAction.GO_TO_ALBUM in actions(track = aTrack(albumName = null, albumId = null)))
        assertFalse(TrackMenuAction.GO_TO_ALBUM in actions(track = aTrack(albumName = "  ", albumId = null)))
        assertFalse(
            TrackMenuAction.GO_TO_ALBUM in actions(canNavigateToAlbum = false),
            "no callback, no row",
        )
    }

    @Test
    fun geometryIsACompactMenuNotTheOldSheet() {
        // Material's own menu ceiling: a menu is sized to its actions, never
        // stretched towards the window it popped up in.
        assertEquals(280.dp, DhunSpacing.menuMaxWidth)
        assertTrue(DhunSpacing.menuMinWidth < DhunSpacing.menuMaxWidth)
        assertTrue(DhunSpacing.menuMaxWidth < DhunSpacing.dialogMaxWidth, "narrower than the sheet it replaced")
        // Rows sit below the 48dp list target on purpose, but stay tappable.
        assertTrue(DhunSpacing.menuRowHeight < DhunSpacing.touchTarget)
        assertTrue(DhunSpacing.menuRowHeight >= 40.dp)
        // The header artwork is a thumbnail, not a hero.
        assertTrue(DhunSpacing.menuArtwork < DhunSpacing.artworkThumb)

        // The tallest menu this policy can produce — all six actions plus the
        // header — has to stay menu-sized on a short phone viewport. This is
        // the number that was ~470dp with the divider and the Close button.
        val tallest = DhunSpacing.menuRowHeight * TrackMenuAction.entries.size +
            DhunSpacing.menuArtwork +
            DhunSpacing.sm * 2 +
            DhunSpacing.smPlus * 2
        assertTrue(tallest < 380.dp, "six actions plus a header came to $tallest — that is a sheet again")
    }
}
