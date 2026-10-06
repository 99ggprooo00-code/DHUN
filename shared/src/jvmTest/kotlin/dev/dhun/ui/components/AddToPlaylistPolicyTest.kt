package dev.dhun.ui.components

import androidx.compose.ui.unit.dp
import dev.dhun.design.DhunSpacing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The playlist picker's height budget, pinned.
 *
 * The device report for FullPlayer → ⋮ → Add to playlist named two things: the
 * old Material 3 look, and "an unusually long/oversized box". The box was real
 * and measurable — the picker reserved a **fixed 180dp** list no matter how many
 * playlists existed, so one or two playlists sat in a mostly empty slab, and the
 * one text field was an M3 `OutlinedTextField`, which is 56dp of Material chrome
 * with a floating label. Both are now content-sized (see [AddToPlaylistPolicy]
 * and the `DhunTextField` in `AddToPlaylistDialog`), which is exactly the
 * class of defect this test keeps fixed: it asserts *reserved* height as a
 * function of row count, with no device involved.
 */
class AddToPlaylistPolicyTest {

    private val rowHeight = DhunSpacing.menuRowHeight

    @Test
    fun thePickerNeverReservesSpaceForRowsItDoesNotHave() {
        assertEquals(0.dp, AddToPlaylistPolicy.listMaxHeight(rowCount = 0), "no playlists → no list box at all")
        assertEquals(rowHeight, AddToPlaylistPolicy.listMaxHeight(rowCount = 1))
        assertEquals(rowHeight * 2, AddToPlaylistPolicy.listMaxHeight(rowCount = 2))
        assertEquals(rowHeight * 4, AddToPlaylistPolicy.listMaxHeight(rowCount = AddToPlaylistPolicy.VISIBLE_ROWS))
    }

    @Test
    fun aFewPlaylistsAreComfortablyShorterThanTheBoxThisReplaced() {
        for (rows in 1..AddToPlaylistPolicy.VISIBLE_ROWS) {
            val height = AddToPlaylistPolicy.listMaxHeight(rows)
            assertTrue(
                height < DhunSpacing.dialogListHeight,
                "the old fixed reservation was ${DhunSpacing.dialogListHeight}; $rows row(s) took $height",
            )
        }
    }

    @Test
    fun morePlaylistsThanFitScrollInsideTheSameBoundedBox() {
        val capped = AddToPlaylistPolicy.listMaxHeight(AddToPlaylistPolicy.VISIBLE_ROWS)
        assertEquals(capped, AddToPlaylistPolicy.listMaxHeight(rowCount = 9))
        assertEquals(capped, AddToPlaylistPolicy.listMaxHeight(rowCount = 40))
        assertTrue(capped <= DhunSpacing.dialogListHeight, "the cap never exceeds the box it replaced")
    }

    @Test
    fun degenerateInputsCannotProduceAnOversizedOrNegativeBox() {
        assertEquals(0.dp, AddToPlaylistPolicy.listMaxHeight(rowCount = -3))
        assertEquals(0.dp, AddToPlaylistPolicy.listMaxHeight(rowCount = 5, rowHeight = 0.dp))
        assertEquals(0.dp, AddToPlaylistPolicy.listMaxHeight(rowCount = 5, rowHeight = -(1f).dp))
        // A nonsense row height degrades to "no reserved box", never to an
        // infinite or negative one that a layout would have to clamp.
        assertEquals(0.dp, AddToPlaylistPolicy.listMaxHeight(rowCount = 5, rowHeight = Float.NaN.dp))
        // A ceiling below the rows wins, so a caller can shrink the box further.
        assertEquals(60.dp, AddToPlaylistPolicy.listMaxHeight(rowCount = 4, ceiling = 60.dp))
    }
}
