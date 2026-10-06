package dev.dhun.ui.player

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.dhun.design.DhunSpacing
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Swipe-down-to-close for the Queue/Related panel, as pure logic.
 *
 * The device report ("≡♪ → Queue/Related: swiping down to close/toggle the
 * panel does not work as expected") was literal: the sheet's grab pill was
 * decoration, no drag detector existed anywhere on the panel, and the player's
 * own queue glyph is hidden with the action row while the panel is open — so
 * the only way out was the ✕. The fix puts the gesture on the header strip and
 * folds it into the *same* transition the open animation uses.
 *
 * These assertions pin the three things that make that safe rather than merely
 * present:
 *
 * 1. the seam between the player's lower boundary and the panel's top edge stays
 *    closed while the finger is down ([panelDragTranslations] moves **both**
 *    halves by the same pixels — moving only the sheet would open a gap of `d`);
 * 2. the offset follows the finger both ways but is clamped to `[0, travel]` —
 *    back up to rest, and never far enough down to peel the sheet off the
 *    screen — and a non-finite value collapses to rest instead of blanking the
 *    panel's translation;
 * 3. the release threshold is a share of that travel, floored at one touch
 *    target and never longer than the panel — and a panel with no travel has no
 *    dismissal gesture at all (any-touch-closes would be a bug, not a feature).
 *
 * The pointer plumbing itself (a `detectVerticalDragGestures` on the grab
 * strip) is not unit-testable without a Compose UI harness; the geometry and
 * the decision it drives are, and those are what regressions break.
 */
class PlayerPanelDragTest {

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.01f, message: String? = null) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            if (message == null) "expected $expected, got $actual (±$tolerance)" else "$message — expected $expected, got $actual (±$tolerance)",
        )
    }

    /* -------- 1. the seam stays closed while the panel is dragged ---------- */

    /**
     * The placement rule the composables implement, with the drag folded in:
     * the sheet's top edge is its own top (`height − travel`) plus the motion's
     * sheet offset, and the player composition's lower boundary is `height`
     * plus the motion's player offset (it is drawn at the inset the rise
     * leaves, then translated).
     */
    private fun seamGapPx(availablePx: Float, travelPx: Float, progress: Float, dragPx: Float): Float {
        val motion = panelDragTranslations(relatedSheetMotion(progress, travelPx), dragPx)
        val sheetTop = availablePx - travelPx + motion.sheetOffsetY
        val playerBottom = availablePx + motion.playerOffsetY
        return sheetTop - playerBottom
    }

    @Test
    fun draggingThePanelDownCarriesThePlayerWithItAndNeverOpensASeam() {
        val available = 1_800f
        val travel = 600f
        val drags = listOf(0f, 1f, 40f, 150f, 599f, 600f)
        val progresses = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        for (progress in progresses) {
            for (drag in drags) {
                assertClose(
                    expected = 0f,
                    actual = seamGapPx(available, travel, progress, drag),
                    message = "progress=$progress drag=$drag",
                )
            }
        }
    }

    @Test
    fun theDragMovesTheSheetAndThePlayerByExactlyTheSameAmount() {
        val base = relatedSheetMotion(progress = 1f, travelPx = 600f)
        val dragged = panelDragTranslations(base, 120f)
        assertClose(expected = base.sheetOffsetY + 120f, actual = dragged.sheetOffsetY)
        assertClose(expected = base.playerOffsetY + 120f, actual = dragged.playerOffsetY)
        assertClose(expected = 1f, actual = dragged.progress, message = "the transition's progress is untouched")
    }

    @Test
    fun anUpwardOrNonFiniteDragIsANoOp() {
        val base = relatedSheetMotion(progress = 1f, travelPx = 600f)
        for (drag in listOf(0f, -1f, -400f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(base, panelDragTranslations(base, drag), "drag=$drag")
        }
    }

    /* -------- 2. the offset: downward only, clamped to the travel ---------- */

    @Test
    fun theOffsetFollowsTheFingerAndStopsAtRestAndAtThePanelsOwnTravel() {
        // A finger pulling down accumulates 1:1…
        assertClose(expected = 40f, actual = panelDragOffsetPx(currentPx = 0f, deltaPx = 40f, travelPx = 600f))
        assertClose(expected = 100f, actual = panelDragOffsetPx(currentPx = 60f, deltaPx = 40f, travelPx = 600f))
        // …never past the panel's travel…
        assertClose(expected = 600f, actual = panelDragOffsetPx(currentPx = 580f, deltaPx = 200f, travelPx = 600f))
        assertClose(expected = 600f, actual = panelDragOffsetPx(currentPx = 600f, deltaPx = 10f, travelPx = 600f))
        // …and never above rest: dragging back up raises the panel again, and a
        // 25px upward movement from a 30px offset leaves 5px, not a negative one.
        assertClose(expected = 0f, actual = panelDragOffsetPx(currentPx = 0f, deltaPx = -50f, travelPx = 600f))
        assertClose(expected = 5f, actual = panelDragOffsetPx(currentPx = 30f, deltaPx = -25f, travelPx = 600f))
        assertClose(expected = 0f, actual = panelDragOffsetPx(currentPx = 30f, deltaPx = -60f, travelPx = 600f))
    }

    @Test
    fun aDroppedFrameKeepsTheOffsetInsteadOfInventingMovement() {
        assertClose(
            expected = 50f,
            actual = panelDragOffsetPx(currentPx = 50f, deltaPx = Float.NaN, travelPx = 600f),
            message = "a non-finite delta is ignored for that frame",
        )
    }

    @Test
    fun anUnmeasurablePanelCannotBeDraggedAtAll() {
        assertClose(expected = 0f, actual = panelDragOffsetPx(50f, 50f, travelPx = 0f), message = "no travel, no drag")
        assertClose(expected = 0f, actual = panelDragOffsetPx(50f, 50f, travelPx = Float.NaN))
        assertClose(expected = 0f, actual = panelDragOffsetPx(-100f, -100f, travelPx = -600f))
    }

    @Test
    fun aCorruptCurrentOffsetCollapsesToRestRatherThanToANaNTranslation() {
        // `sheetDragPx` is only ever written from clamped values and animations,
        // so this cannot happen in practice — but if it did, a NaN translation
        // would blank the panel. Rest is the only safe answer, and the next
        // frame's delta drags again from there.
        assertClose(expected = 0f, actual = panelDragOffsetPx(Float.NaN, 50f, travelPx = 600f))
        assertClose(expected = 0f, actual = panelDragOffsetPx(Float.NEGATIVE_INFINITY, 50f, travelPx = 600f))
    }

    /* -------- 3. the release threshold ------------------------------------ */

    /** 48dp at a phone's 2.75× density — the pointer floor the design language uses. */
    private fun touchTargetPx(density: Float): Float =
        with(Density(density)) { DhunSpacing.touchTarget.toPx() }

    @Test
    fun theThresholdIsAShareOfTheTravelFlooredAtOneTouchTarget() {
        val touchTarget = touchTargetPx(2.75f) // 132px
        // Tall panel: the fraction governs (0.28 × 1200 = 336 > 132).
        assertClose(expected = 1_200f * PANEL_DISMISS_TRAVEL_FRACTION, actual = panelDismissThresholdPx(1_200f, touchTarget))
        // Short panel: the floor governs (0.28 × 300 = 84 < 132) so a thumb
        // still has a reachable gesture.
        assertClose(expected = touchTarget, actual = panelDismissThresholdPx(300f, touchTarget))
        // Travel shorter than the floor: the threshold is the travel itself, and
        // never more than it — the panel cannot demand a drag it has no room for.
        assertClose(expected = 100f, actual = panelDismissThresholdPx(100f, touchTarget))
        // No floor at all (never the case on a real density): the fraction governs.
        assertClose(expected = 100f * PANEL_DISMISS_TRAVEL_FRACTION, actual = panelDismissThresholdPx(100f, touchTargetPx = 0f))
    }

    @Test
    fun aPanelWithNoTravelHasNoDismissalGesture() {
        assertEquals(0f, panelDismissThresholdPx(0f, touchTargetPx(2.75f)))
        assertEquals(0f, panelDismissThresholdPx(Float.NaN, touchTargetPx(2.75f)))
        assertEquals(0f, panelDismissThresholdPx(-10f, touchTargetPx(2.75f)))
        // …and a zero threshold does not mean "every touch closes it".
        assertFalse(shouldDismissPanel(dragPx = 0f, thresholdPx = 0f))
        assertFalse(shouldDismissPanel(dragPx = 200f, thresholdPx = 0f))
    }

    @Test
    fun releasingPastTheThresholdClosesAndAnythingShorterSnapsBack() {
        val threshold = panelDismissThresholdPx(1_200f, touchTargetPx(2.75f))
        assertTrue(shouldDismissPanel(dragPx = threshold, thresholdPx = threshold), "exactly at the threshold commits")
        assertTrue(shouldDismissPanel(dragPx = threshold + 1f, thresholdPx = threshold))
        assertFalse(shouldDismissPanel(dragPx = threshold - 1f, thresholdPx = threshold))
        assertFalse(shouldDismissPanel(dragPx = 0f, thresholdPx = threshold), "a tap on the header is not a swipe")
        assertFalse(shouldDismissPanel(dragPx = -threshold, thresholdPx = threshold), "upward never dismisses")
        assertFalse(shouldDismissPanel(dragPx = Float.NaN, thresholdPx = threshold))
        assertFalse(shouldDismissPanel(dragPx = threshold, thresholdPx = Float.NaN))
    }

    @Test
    fun aRealisticPhoneFlickClosesThePanelAndAShortNudgeDoesNot() {
        // 393dp-wide Android phone: travel is a share of the room above the
        // measured chrome — take the sheet floor (280dp) as the short case.
        val density = 2.75f
        val touchTarget = touchTargetPx(density)
        val travelPx = with(Density(density)) { 280.dp.toPx() } // 770px
        val threshold = panelDismissThresholdPx(travelPx, touchTarget)
        assertTrue(shouldDismissPanel(dragPx = 400f, thresholdPx = threshold), "a real flick (≈145dp) closes it")
        assertFalse(shouldDismissPanel(dragPx = 60f, thresholdPx = threshold), "a 22dp nudge snaps back")
        // The catch: a drag all the way down must land at the same offset the
        // close animation starts from — the travel — not beyond it.
        assertClose(expected = travelPx, actual = panelDragOffsetPx(0f, travelPx * 2f, travelPx))
    }
}
