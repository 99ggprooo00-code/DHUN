package dev.dhun.ui.player

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.dhun.design.DhunSpacing
import dev.dhun.design.FullPlayerLayoutMode
import dev.dhun.design.fittedPlayerArtworkSize
import dev.dhun.design.fullPlayerLayoutMode
import dev.dhun.design.playerWideControlsWidth
import dev.dhun.design.usesCompactPlayerControls
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Player chrome + Queue/Related sheet geometry, as pure logic.
 *
 * 1. the sharp artwork is sized by [fittedPlayerArtworkSize] and drawn `Fit`,
 *    so nothing is cropped by the *layout*;
 * 2. the sheet's height and the Full Player's rise are **the same measured
 *    number** ([relatedSheetTravel] from the safe-area height and the
 *    density-corrected [chromeHeightDp]), so one reversible
 *    [relatedSheetMotion] moves the whole player composition and the panel
 *    together and their boundaries meet on every frame. That shared number is
 *    the difference between "a panel rose while the player stood still, and
 *    only the cover slid out of the way" and a single composition getting out
 *    of the sheet's way;
 * 3. the blurred backdrop stays clear in the middle (so the artwork glows)
 *    and darkens towards the bottom (so titles/progress/transport read).
 *
 * Why geometry rather than screenshots: the contract is about *responsive*
 * measurements — safe-area height after Android's bars and cutout, display
 * density, the measured chrome, a dragged Desktop window — that one emulator
 * frame cannot prove, and that Android and Desktop must agree on. [seamGapAt]
 * states the placement rule the composables implement, which is what these
 * assertions are measured against.
 */
class PlayerSheetLayoutTest {

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.01f, message: String? = null) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            if (message == null) "expected $expected, got $actual (±$tolerance)" else "$message — expected $expected, got $actual (±$tolerance)",
        )
    }

    /* -------- bug 1: fit-to-card artwork, both axes ---------------------- */

    @Test
    fun heroArtworkUsesThePaddedFieldOnPhoneAndDesktopAndNeverOverflows() {
        // (field width, field height) as measured inside the hero stage.
        val fields = listOf(
            328.dp to 380.dp, // 360dp phone after the 16dp side inset
            361.dp to 520.dp, // 393dp phone: 91.9% of the safe content width
            688.dp to 760.dp, // 720dp content column after the same inset
            1_400.dp to 800.dp, // huge window: the token caps it
            120.dp to 240.dp, // narrow pane
        )
        fields.forEach { (width, height) ->
            val size = fittedPlayerArtworkSize(width, height)
            assertTrue(size <= width, "hero $size must fit a $width field")
            assertTrue(size <= height, "hero $size must fit a $height field")
            assertTrue(size <= DhunSpacing.playerArtworkMaxSize)
            assertEquals(minOf(width, height, DhunSpacing.playerArtworkMaxSize), size)
        }
        assertEquals(0.dp, fittedPlayerArtworkSize(320.dp, 0.dp), "no field, no artwork")
    }

    @Test
    fun portraitHeroKeepsTheCoverInTheRequestedWidthRange() {
        val phoneWidths = listOf(360.dp, 393.dp)
        phoneWidths.forEach { screenWidth ->
            val stageWidth = screenWidth -
                DhunSpacing.playerArtworkHorizontalInset * 2 -
                DhunSpacing.playerArtworkAnimationInset * 2
            val cover = fittedPlayerArtworkSize(stageWidth, 600.dp)
            val fraction = cover.value / screenWidth.value
            assertTrue(
                fraction in 0.80f..0.92f,
                "$cover should be 80–92% of a $screenWidth portrait player, was ${fraction * 100}%",
            )
        }
    }

    @Test
    fun wideViewportsGiveArtworkHeightInsteadOfStarvingItAboveChrome() {
        assertEquals(FullPlayerLayoutMode.Stacked, fullPlayerLayoutMode(393.dp, 823.dp))
        assertEquals(FullPlayerLayoutMode.Wide, fullPlayerLayoutMode(873.dp, 393.dp))
        assertEquals(FullPlayerLayoutMode.Wide, fullPlayerLayoutMode(1_200.dp, 780.dp))
        assertEquals(FullPlayerLayoutMode.Stacked, fullPlayerLayoutMode(479.dp, 320.dp))
        assertEquals(FullPlayerLayoutMode.Stacked, fullPlayerLayoutMode(480.dp, 480.dp))

        // A compact landscape control column leaves a real artwork pane; it
        // never asks the stacked layout to squeeze art above all controls.
        val controls = playerWideControlsWidth(873.dp)
        assertTrue(controls >= DhunSpacing.playerWideControlsMinWidth)
        assertTrue(controls <= DhunSpacing.playerWideControlsMaxWidth)
        assertTrue(controls + DhunSpacing.playerWideLayoutGap < 873.dp)
    }

    @Test
    fun shortViewportsCompactOnlySpacingNotTouchTargets() {
        assertTrue(usesCompactPlayerControls(DhunSpacing.playerCompactControlsHeight))
        assertFalse(usesCompactPlayerControls(DhunSpacing.playerCompactControlsHeight + 1.dp))
        assertFalse(usesCompactPlayerControls(0.dp))
        assertEquals(0.dp, playerWideControlsWidth(0.dp))
    }

    /* -------- bug 2: one measured travel for the sheet AND the player ----- */

    @Test
    fun sheetHeightIsTheTravelAndLeavesTheMeasuredChromeAboveIt() {
        // 393×873dp portrait phone with ~250dp of measured chrome.
        val travel = relatedSheetTravel(availableHeight = 823.dp, chromeHeight = 250.dp)
        assertTrue(travel > DhunSpacing.zero, "a real phone must get a real sheet")
        assertClose(expected = 0.6f * 573f, actual = travel.value, message = "the sheet claims its share of the room above the chrome")
        // Travel == sheet height, so the number the panel is sized by is the
        // same number the player rises by — there is no second distance that
        // can drift away from the first.
        assertTrue(travel + 250.dp <= 823.dp, "sheet + chrome must never overflow the safe area")
        assertTrue(823.dp - travel >= 250.dp, "the rising chrome stays entirely above the open panel")
        assertTrue(
            823.dp - travel >= DhunSpacing.queuePanelPlayerBandFloor + 250.dp,
            "…with a band of cover left above it",
        )
    }

    @Test
    fun travelNeverEatsThePlayerOnAShortViewport() {
        // Landscape phone: 80dp of room above the chrome. The sheet takes what
        // it has instead of pushing the composition off the top.
        val tight = relatedSheetTravel(availableHeight = 400.dp, chromeHeight = 320.dp)
        assertEquals(80.dp, tight, "a cramped viewport shrinks the panel, it never overflows")

        // Chrome as tall as the viewport: nothing to vacate, so no travel and
        // therefore no panel at all.
        assertEquals(0.dp, relatedSheetTravel(300.dp, 480.dp))
        assertEquals(0.dp, relatedSheetTravel((-40).dp, 100.dp))

        // An unmeasured/unbounded axis must not invent a distance either.
        assertEquals(0.dp, relatedSheetTravel(Float.NaN.dp, 100.dp))
        assertEquals(0.dp, relatedSheetTravel(800.dp, Float.POSITIVE_INFINITY.dp))

        // A 600dp phone cannot host a 280dp panel *and* keep its cover band: the
        // readable-panel floor wins there, and the chrome is still not buried.
        val small = relatedSheetTravel(availableHeight = 600.dp, chromeHeight = 248.dp)
        assertEquals(DhunSpacing.queuePanelMinHeight, small)
        assertTrue(600.dp - small >= 248.dp, "transport must stay above the panel")
    }

    @Test
    fun travelIsBoundedByTheViewportAndByThePlayersOwnNeeds() {
        val viewports = listOf(
            "small Android portrait" to (590.dp to 248.dp),
            "large Android portrait" to (860.dp to 252.dp),
            "Android tablet portrait" to (1_180.dp to 260.dp),
            "Android landscape" to (340.dp to 210.dp),
            "wide Desktop" to (780.dp to 300.dp),
            "maximised Desktop" to (1_080.dp to 320.dp),
            "resized Desktop" to (520.dp to 280.dp),
            "cramped Desktop" to (300.dp to 190.dp),
            "square phone" to (640.dp to 300.dp),
        )
        viewports.forEach { (name, axes) ->
            val (available, chrome) = axes
            val travel = relatedSheetTravel(available, chrome)
            val room = available - chrome

            assertTrue(travel >= DhunSpacing.zero, "$name: travel cannot be negative")
            assertTrue(travel + chrome <= available, "$name: the panel must not overflow the safe area")
            assertTrue(
                available - travel >= chrome,
                "$name: the chrome it rises to make room for must stay on screen",
            )
            assertTrue(
                travel >= DhunSpacing.queuePanelMinHeight.coerceAtMost(room),
                "$name: never below the readable-panel floor while the room exists",
            )
            val bandCap = (room - DhunSpacing.queuePanelPlayerBandFloor).coerceAtLeast(DhunSpacing.zero)
            if (bandCap >= DhunSpacing.queuePanelMinHeight.coerceAtMost(room)) {
                assertTrue(travel <= bandCap, "$name: the player band cap must hold when it can")
            }
        }
    }

    @Test
    fun floorAppliesOnlyWhileTheRoomExistsAndTheCapNeverInverts() {
        val lowFraction = relatedSheetTravel(availableHeight = 823.dp, chromeHeight = 330.dp, fraction = 0.01f)
        assertEquals(DhunSpacing.queuePanelMinHeight, lowFraction, "a stingy fraction still shows a readable panel")

        val highFraction = relatedSheetTravel(availableHeight = 823.dp, chromeHeight = 330.dp, fraction = 4f)
        // An out-of-range fraction clamps to the whole room; the band cap trims
        // it so the player keeps its cover, so the result is the cap.
        assertEquals(823.dp - 330.dp - DhunSpacing.queuePanelPlayerBandFloor, highFraction)
    }

    @Test
    fun measuredChromePixelsBecomeDpBeforeTheyAreUsedAsTravel() {
        // Android: 907px of chrome at density 2.75 is 330dp, NOT 907dp.
        val androidChrome = chromeHeightDp(pixelHeight = 907, density = 2.75f)
        assertClose(expected = 329.8f, actual = androidChrome.value, tolerance = 0.05f)

        // Desktop at density 1.0 is unchanged.
        assertClose(expected = 330f, actual = chromeHeightDp(330, 1f).value)

        // The regression this guards: a raw pixel count used as dp ate the
        // whole panel, so Android showed an empty translucent bar and Desktop a
        // thin sliver. Density-correct, the sheet keeps its share of the room.
        val travel = relatedSheetTravel(availableHeight = 823.dp, chromeHeight = androidChrome)
        assertTrue(travel > DhunSpacing.zero, "the sheet must survive a real phone density")
        assertClose(
            expected = 0.6f * (823f - androidChrome.value),
            actual = travel.value,
            tolerance = 0.5f,
            message = "the panel claims 60% of the room above the measured chrome",
        )
    }

    @Test
    fun degenerateDensitiesAndHeightsStayInsideTheLayout() {
        assertEquals(0.dp, chromeHeightDp(-10, 2.75f))
        assertEquals(0.dp, chromeHeightDp(0, 2.75f))
        assertClose(expected = 100f, actual = chromeHeightDp(100, 0f).value, tolerance = 0.001f)
        assertClose(expected = 100f, actual = chromeHeightDp(100, Float.NaN).value, tolerance = 0.001f)
        assertClose(expected = 100f, actual = chromeHeightDp(100, Float.POSITIVE_INFINITY).value, tolerance = 0.001f)
    }

    @Test
    fun playerAndSheetUseOneReversibleMeasuredTravelDistance() {
        val closed = relatedSheetMotion(progress = 0f, travelPx = 1_200f)
        assertEquals(0f, closed.playerOffsetY)
        assertEquals(1_200f, closed.sheetOffsetY)
        assertEquals(0f, closed.progress)

        val halfway = relatedSheetMotion(progress = 0.5f, travelPx = 1_200f)
        assertEquals(-600f, halfway.playerOffsetY)
        assertEquals(600f, halfway.sheetOffsetY)
        assertEquals(0.5f, halfway.progress)

        val open = relatedSheetMotion(progress = 1f, travelPx = 1_200f)
        assertEquals(-1_200f, open.playerOffsetY)
        assertEquals(0f, open.sheetOffsetY)
        assertEquals(1f, open.progress)

        // A reversed target uses the same geometry rather than a second offset
        // or a one-way enter/exit animation.
        val reversed = relatedSheetMotion(progress = 0.25f, travelPx = 1_200f)
        assertEquals(-300f, reversed.playerOffsetY)
        assertEquals(900f, reversed.sheetOffsetY)
    }

    @Test
    fun artworkFieldAbsorbsTheSameRiseTheCompositionTravels() {
        // Half the player's rise is taken out of the weighted artwork field, so
        // the cover re-fits instead of being cropped and the collapse header is
        // never pushed out of the touch area. It is the *same* progress and the
        // *same* travel as the offset, which is what keeps the two halves of the
        // motion from ever disagreeing.
        val travel = 344.dp
        assertEquals(0.dp, relatedSheetLayoutRiseDp(travel, 0f), "the first opening frame is the untouched layout")
        assertEquals(172.dp, relatedSheetLayoutRiseDp(travel, 0.5f))
        assertEquals(travel, relatedSheetLayoutRiseDp(travel, 1f), "open, the field has given up exactly the travel")
        assertEquals(0.dp, relatedSheetLayoutRiseDp(travel, 0f), "…and after a close it has given back exactly the travel")

        // At density 1 the inset and the offset must cancel out to the same
        // number in either direction, which is what the player's rise *is*.
        listOf(0f, 0.2f, 0.5f, 0.81f, 1f).forEach { progress ->
            val risePx = relatedSheetLayoutRiseDp(344.dp, progress).value
            val offsetPx = -relatedSheetMotion(progress, 344f).playerOffsetY
            assertClose(
                expected = offsetPx,
                actual = risePx,
                tolerance = 0.02f,
                message = "progress $progress: layout inset and drawn offset must be one distance",
            )
        }

        // Monotonic in the same direction as the offset (no overshoot to fight),
        // and nothing a broken frame can turn into a negative inset.
        val rises = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { relatedSheetLayoutRiseDp(travel, it).value }
        assertTrue(rises.zipWithNext { a, b -> b >= a }.all { it }, "rise must not oscillate: $rises")
        assertEquals(0.dp, relatedSheetLayoutRiseDp(travel, Float.NaN))
        assertEquals(0.dp, relatedSheetLayoutRiseDp(travel, -1f))
        assertEquals(travel, relatedSheetLayoutRiseDp(travel, 3f), "progress clamps, it never overshoots")
        assertEquals(0.dp, relatedSheetLayoutRiseDp(Float.NaN.dp, 0.5f))
        assertEquals(0.dp, relatedSheetLayoutRiseDp((-90).dp, 0.5f))
    }

    @Test
    fun absorbedRiseNeverExceedsTheRoomThePlayerCanGiveUp() {
        // The rise is only safe while it comes out of the field *above* the
        // measured chrome: beyond that the timeline and transport would be
        // squeezed out of their own column. Because travel is capped by that
        // room, no progress of the transition can ask for more.
        val viewports = listOf(
            590.dp to 248.dp,
            860.dp to 252.dp,
            1_180.dp to 260.dp,
            340.dp to 210.dp,
            780.dp to 300.dp,
            520.dp to 280.dp,
            300.dp to 190.dp,
        )
        viewports.forEach { (available, chrome) ->
            val travel = relatedSheetTravel(available, chrome)
            val absorbableRoom = (available - chrome).coerceAtLeast(DhunSpacing.zero)
            listOf(0f, 0.25f, 0.5f, 0.8f, 1f).forEach { progress ->
                val rise = relatedSheetLayoutRiseDp(travel, progress)
                assertTrue(rise <= travel, "$available/$chrome: rise $rise exceeded its travel $travel")
                assertTrue(
                    rise <= absorbableRoom,
                    "$available/$chrome: rise $rise cannot be absorbed by $absorbableRoom of artwork field",
                )
                assertClose(
                    expected = 0f,
                    actual = seamGapAt(available.value, travel.value, progress),
                    tolerance = 0.02f,
                    message = "$available/$chrome at $progress: the chrome's bottom must still meet the panel's top",
                )
            }
        }
    }

    @Test
    fun openingAndClosingAreTheSameGeometryRunInOppositeDirections() {
        val travelPx = 940f
        val availablePx = 2_260f // 823dp at density 2.75
        val samples = listOf(0f, 0.1f, 0.25f, 0.5f, 0.75f, 0.9f, 1f)

        // Positions are a function of progress alone, so the closing frames are
        // the opening frames read backwards. Nothing here can make the two
        // diverge the way a separate enter/exit pair can.
        val opening = samples.map { playerAndSheetEdges(availablePx, travelPx, it) }
        val closing = samples.asReversed().map { playerAndSheetEdges(availablePx, travelPx, it) }
        opening.zip(closing.asReversed()).forEach { (a, b) ->
            assertEquals(a, b, "reversing the target must reproduce exactly the same frames")
        }

        // …and the two halves really are one distance: whatever the player has
        // gained, the panel has given up.
        samples.forEach { progress ->
            val motion = relatedSheetMotion(progress, travelPx)
            assertClose(expected = -travelPx * progress, actual = motion.playerOffsetY, tolerance = 0.001f)
            assertClose(expected = travelPx * (1f - progress), actual = motion.sheetOffsetY, tolerance = 0.001f)
        }
    }

    @Test
    fun seamStaysClosedForEveryViewportFromFirstFrameToLast() {
        // name, safe-area height, measured chrome, density. Android's status
        // bar / cutout / gesture inset is already out of the height (the player
        // is laid out inside safeDrawingPadding); Desktop passes the raw window.
        val viewports = listOf(
            Triple("small Android portrait", 590.dp to 248.dp, 2.75f),
            Triple("large Android portrait", 860.dp to 252.dp, 2.625f),
            Triple("Android tablet portrait", 1_180.dp to 260.dp, 2f),
            Triple("Android landscape", 340.dp to 210.dp, 2.75f),
            Triple("wide Desktop", 780.dp to 300.dp, 1f),
            Triple("maximised Desktop", 1_080.dp to 320.dp, 1f),
            Triple("resized Desktop", 520.dp to 280.dp, 1f),
            Triple("cramped Desktop", 300.dp to 190.dp, 1f),
        )
        val progressValues = listOf(0f, 0.12f, 0.35f, 0.5f, 0.66f, 0.9f, 1f)

        viewports.forEach { (name, axes, density) ->
            val (availableDp, chromeDp) = axes
            val travelDp = relatedSheetTravel(availableDp, chromeDp)
            // Same conversion the composable uses: dp is decided once, the
            // offsets are painted in px, so rounding can never open a seam.
            val availablePx = with(Density(density, 1f)) { availableDp.toPx() }
            val travelPx = with(Density(density, 1f)) { travelDp.toPx() }

            if (travelPx <= 0f) {
                assertFalse(
                    relatedSheetTransition(targetOpen = true, progress = 1f, travelPx = travelPx).sheetMounted,
                    "$name has no travel, so it must not mount a sheet at all",
                )
                return@forEach
            }

            progressValues.forEach { progress ->
                assertClose(
                    expected = 0f,
                    actual = seamGapAt(availablePx, travelPx, progress),
                    tolerance = 0.02f,
                    message = "$name at progress $progress: the panel's top must sit exactly on the player's lower boundary — no gap, no overlap",
                )
            }

            // The first opening frame is the untouched layout with the panel
            // parked entirely outside the clipped box: nothing has jumped to its
            // final position and nothing is peeking into view yet.
            assertEquals(0f, relatedSheetMotion(0f, travelPx).playerOffsetY, "$name: the player starts at rest")
            assertClose(expected = availablePx, actual = playerAndSheetEdges(availablePx, travelPx, 0f).second, tolerance = 0.02f, message = "$name: the sheet starts flush with the bottom edge")

            // Open, the panel is flush with the bottom of the safe area (no gap
            // beneath it) and its top is exactly the player's lower boundary.
            val open = playerAndSheetEdges(availablePx, travelPx, 1f)
            assertClose(expected = availablePx - travelPx, actual = open.first, tolerance = 0.02f)
            assertClose(
                expected = availablePx,
                actual = open.second + travelPx,
                tolerance = 0.02f,
                message = "$name: the open sheet must end flush with the bottom edge",
            )
            assertTrue(travelPx <= availablePx + 0.5f, "$name: the panel cannot overflow the viewport")
        }
    }

    @Test
    fun rapidReversalContinuesFromCurrentProgressWithoutJumping() {
        val travelPx = 940f
        // A 300ms tween hands ~18 frames; the target flips mid-opening.
        val opening = listOf(0f, 0.2f, 0.45f, 0.68f)
        val closingAfterReversal = listOf(0.68f, 0.5f, 0.28f, 0.1f, 0f)

        val openingFrames = opening.map { relatedSheetTransition(true, it, travelPx) }
        val closingFrames = closingAfterReversal.map { relatedSheetTransition(false, it, travelPx) }

        // The reversal frame *is* the same frame: reversing the target neither
        // restarts the motion at zero nor snaps to either end.
        assertEquals(openingFrames.last().motion, closingFrames.first().motion)
        assertClose(
            expected = -travelPx * 0.68f,
            actual = closingFrames.first().motion.playerOffsetY,
            message = "the player keeps its current offset when the target reverses",
        )

        // Motion stays monotonic in both directions, so nothing doubles back.
        assertTrue(openingFrames.map { it.motion.playerOffsetY }.zipWithNext { a, b -> b <= a }.all { it })
        assertTrue(closingFrames.map { it.motion.playerOffsetY }.zipWithNext { a, b -> b >= a }.all { it })

        // The panel is mounted for the whole reversal, so it never has to
        // remount (and re-measure) mid-motion…
        (openingFrames + closingFrames.dropLast(1)).forEach { frame ->
            assertTrue(frame.sheetMounted, "a reversal must find the sheet already in the tree")
            assertFalse(frame.actionRowVisible, "the action row stays hidden through the reversal")
        }
        // …and only the landing frame restores the player.
        val landed = closingFrames.last()
        assertFalse(landed.sheetMounted)
        assertTrue(landed.actionRowVisible)
        assertEquals(0f, landed.motion.playerOffsetY)
        assertClose(expected = travelPx, actual = landed.motion.sheetOffsetY, message = "the closed panel parks one travel below the edge")
    }

    @Test
    fun travelIsCapturedBeforeOpeningAndStableForTheWholeFlight() {
        val measuredWhileIdle = 344.dp
        val resizedMidFlight = 260.dp

        // Idle frames track the responsive measurement (rotation, a dragged
        // Desktop window, a chrome that re-measures after a track change)…
        assertEquals(
            measuredWhileIdle,
            relatedSheetTravelDp(inFlight = false, liveDp = measuredWhileIdle, frozenDp = measuredWhileIdle),
        )
        // …and the distance used on the first opening frame is the one captured
        // *before* the target flipped, so the panel mounts at its final size
        // instead of growing into place behind the animation.
        assertEquals(
            measuredWhileIdle,
            relatedSheetTravelDp(inFlight = true, liveDp = resizedMidFlight, frozenDp = measuredWhileIdle),
        )
        listOf(0.1f, 0.5f, 0.9f, 1f).forEach { progress ->
            assertEquals(
                measuredWhileIdle,
                relatedSheetTravelDp(inFlight = true, liveDp = resizedMidFlight, frozenDp = measuredWhileIdle),
                "travel must not move under progress $progress",
            )
        }
        // Once the sheet has landed closed, live geometry is captured again.
        assertEquals(
            resizedMidFlight,
            relatedSheetTravelDp(inFlight = false, liveDp = resizedMidFlight, frozenDp = measuredWhileIdle),
        )
    }

    @Test
    fun unmeasuredTravelFallsBackToLiveGeometryInsteadOfAZeroMotion() {
        // First composition ever: nothing frozen yet, so the live measurement
        // is used and the very first opening still moves.
        assertEquals(280.dp, relatedSheetTravelDp(inFlight = true, liveDp = 280.dp, frozenDp = 0.dp))
        // Non-finite / negative live values are never promoted to a travel.
        assertEquals(0.dp, relatedSheetTravelDp(inFlight = false, liveDp = (-40).dp, frozenDp = 0.dp))
        assertEquals(0.dp, relatedSheetTravelDp(inFlight = false, liveDp = Float.NaN.dp, frozenDp = 0.dp))
        assertEquals(0.dp, relatedSheetTravelDp(inFlight = true, liveDp = 0.dp, frozenDp = Float.NEGATIVE_INFINITY.dp))
        // A single unmeasurable frame mid-flight must not move the goal posts:
        // the frozen distance is what the animation is running against.
        assertEquals(280.dp, relatedSheetTravelDp(inFlight = true, liveDp = Float.NaN.dp, frozenDp = 280.dp))
    }

    @Test
    fun actionRowIsHiddenForTheWholeSheetTransitionAndRestoredOnce() {
        // Opening, open, and the tail of a close all hide the queue / shuffle /
        // repeat / lyrics row — including the first frame, where progress is
        // still zero but the target has already moved.
        assertFalse(relatedSheetTransition(targetOpen = true, progress = 0f, travelPx = 940f).actionRowVisible)
        listOf(0.05f, 0.5f, 0.95f, 1f).forEach { progress ->
            val frame = relatedSheetTransition(true, progress, 940f)
            assertFalse(frame.actionRowVisible, "hidden at progress $progress")
            assertTrue(frame.sheetMounted, "mounted at progress $progress")
        }
        // Closing keeps it hidden until the motion reaches exactly zero, which
        // is what stops the row flickering back on for the last few frames.
        assertFalse(relatedSheetTransition(false, 0.01f, 940f).actionRowVisible)
        assertTrue(relatedSheetTransition(false, 0f, 940f).actionRowVisible)
    }

    @Test
    fun closedPlayerLayoutIsRestoredExactlyAfterClosing() {
        // The player's only transform during the transition is the offset, so
        // "offset 0, nothing mounted, action row back" *is* the untouched
        // layout: same column, same chrome, same measured height.
        val atRest = relatedSheetTransition(targetOpen = false, progress = 0f, travelPx = 940f)
        assertFalse(atRest.sheetMounted)
        assertTrue(atRest.actionRowVisible)
        assertEquals(0f, atRest.motion.playerOffsetY)
        assertEquals(0f, atRest.motion.progress)
        assertClose(expected = 940f, actual = atRest.motion.sheetOffsetY)

        // A sheet with no room to travel in never mounts, so the player and its
        // action row are left exactly as they were.
        val noRoom = relatedSheetTransition(targetOpen = true, progress = 1f, travelPx = 0f)
        assertFalse(noRoom.sheetMounted)
        assertTrue(noRoom.actionRowVisible)
        assertEquals(0f, noRoom.motion.playerOffsetY)
        assertEquals(0f, noRoom.motion.sheetOffsetY)
    }

    @Test
    fun arrivingSongsCannotResizeThePanelMidFlight() {
        // The panel's height is a function of viewport + chrome only — never of
        // how much content it holds — and the frozen travel keeps that true for
        // a queue that fills in, or a Related list that lands, *while* the
        // transition is running. That is what stops the sheet being mounted at a
        // zero/unknown height and re-geometry'd under the animation.
        val travel = relatedSheetTravel(availableHeight = 823.dp, chromeHeight = 250.dp)
        listOf(0, 1, 3, 12, 40).forEach { songCount ->
            assertEquals(travel, relatedSheetTravel(823.dp, 250.dp), "$songCount songs must not change it")
            // Even a live measurement that grows as rows land (the failure mode
            // behind a panel that re-measures under its own animation) cannot
            // move the distance once the flight has started.
            val growingLiveMeasurement = travel + DhunSpacing.xs * songCount
            assertEquals(
                travel,
                relatedSheetTravelDp(inFlight = true, liveDp = growingLiveMeasurement, frozenDp = travel),
                "content arriving at frame $songCount must not move the travel",
            )
        }

        // The sheet's own fixed chrome (grab pill, title row, tab row) is paid
        // for out of the panel height, so even the *floor* leaves a full row.
        val fixedSheetChrome = DhunSpacing.sm + DhunSpacing.xsPlus + DhunSpacing.huge + DhunSpacing.touchTarget
        assertTrue(
            travel - fixedSheetChrome >= DhunSpacing.listRowHeight,
            "$travel of panel must leave a ${DhunSpacing.listRowHeight} row below its header and tabs",
        )
        assertTrue(
            DhunSpacing.queuePanelMinHeight - fixedSheetChrome >= DhunSpacing.listRowHeight,
            "the readable floor itself must hold a row",
        )
    }

    @Test
    fun invalidTransitionGeometryDegradesToAStableClosedState() {
        val invalidProgress = relatedSheetMotion(progress = Float.NaN, travelPx = Float.POSITIVE_INFINITY)
        assertEquals(0f, invalidProgress.playerOffsetY)
        assertEquals(0f, invalidProgress.sheetOffsetY)
        assertEquals(0f, invalidProgress.progress)

        val clamped = relatedSheetMotion(progress = 2f, travelPx = -10f)
        assertEquals(0f, clamped.playerOffsetY)
        assertEquals(0f, clamped.sheetOffsetY)
        assertEquals(1f, clamped.progress)

        // An invalid travel never mounts a panel with nonsense geometry.
        val invalid = relatedSheetTransition(targetOpen = true, progress = Float.NaN, travelPx = Float.NaN)
        assertFalse(invalid.sheetMounted)
        assertTrue(invalid.actionRowVisible)
    }

    /* -------- bug 3: blurred backdrop, dark at the bottom ---------------- */

    @Test
    fun ambientScrimGlowsThroughTheMiddleAndDarkensTheControlCluster() {
        val stops = playerAmbientScrimStops()

        assertEquals(0f, stops.first().first)
        assertEquals(1f, stops.last().first)
        stops.forEach { (offset, alpha) ->
            assertTrue(offset in 0f..1f, "offset $offset out of range")
            assertTrue(alpha in 0f..1f, "alpha $alpha out of range")
        }
        stops.zipWithNext { (offsetA, _), (offsetB, _) ->
            assertTrue(offsetB > offsetA, "stops must ascend: $offsetA then $offsetB")
        }

        // Middle of the screen: the blurred artwork must read through.
        val middle = stops.first { it.first in 0.30f..0.50f }
        assertEquals(0f, middle.second, "the backdrop stays untouched behind the artwork field")

        // Bottom: dark enough for text/transport, but never a flat black wall.
        val bottom = stops.last().second
        assertTrue(bottom >= 0.85f, "bottom scrim too light for chrome: $bottom")
        assertTrue(bottom < 1f, "bottom scrim kills the artwork: $bottom")

        // And it must darken on the way down, so there is no bright band behind the controls.
        val lowerBand = stops.filter { it.first >= 0.55f }.map { it.second }
        lowerBand.zipWithNext { a, b -> assertTrue(b >= a, "scrim lightens towards the bottom: $a then $b") }
        assertTrue(lowerBand.first() > 0f, "the darkening must start above the very bottom edge")
    }

    @Test
    fun playerBackdropDimLetsArtworkThroughWithoutGoingClear() {
        // The flat black over the bleed was 0.16 / 0.52. It has to stay under
        // those — that is the "artwork shows through" contract — and above a
        // floor, so a bright cover cannot erase the control cluster entirely.
        // The ambient scrim, not this dim, carries the bottom-edge legibility.
        assertTrue(PLAYER_BACKDROP_DIM in 0.04f..0.12f, "normal dim out of range: $PLAYER_BACKDROP_DIM")
        assertTrue(
            PLAYER_BACKDROP_DIM_LYRICS in 0.28f..0.48f,
            "lyrics dim out of range: $PLAYER_BACKDROP_DIM_LYRICS",
        )
        assertTrue(
            PLAYER_BACKDROP_DIM < PLAYER_BACKDROP_DIM_LYRICS,
            "lyrics mode must stay darker than the plain player",
        )
    }

    @Test
    fun listThumbFitsTheFixedRowBudget() {
        // artworkThumb is the wrapping-row size (TrackRow). The fixed 72dp
        // queue slot uses touchTarget, but 64 + the 4dp inset must still fit
        // that slot so a later swap cannot clip.
        assertEquals(64.dp, DhunSpacing.artworkThumb)
        assertTrue(
            DhunSpacing.artworkThumb + DhunSpacing.xs * 2 <= DhunSpacing.listRowHeight,
            "thumb ${DhunSpacing.artworkThumb} plus the queue inset exceeds ${DhunSpacing.listRowHeight}",
        )
        assertTrue(
            DhunSpacing.artworkThumb > DhunSpacing.touchTarget,
            "the list thumb must stay larger than the compact 48dp slot",
        )
    }

    @Test
    fun playerBackdropPolicySuppressesSharpArtworkWhenBlurIsUnsupported() {
        // Platforms without RenderEffect (Android < API 31) fall back to the clean dark surface.
        assertTrue(shouldRenderPlayerBackdrop("https://example.com/art.jpg", supportsBlur = true))
        assertFalse(shouldRenderPlayerBackdrop("https://example.com/art.jpg", supportsBlur = false))
        assertFalse(shouldRenderPlayerBackdrop(null, supportsBlur = true))
        assertFalse(shouldRenderPlayerBackdrop("", supportsBlur = true))
        assertFalse(shouldRenderPlayerBackdrop("   ", supportsBlur = true))
    }

    /* -------- queue rows ------------------------------------------------- */

    @Test
    fun queueRowsShowADurationOnlyWhenItIsKnown() {
        assertEquals("3:45", queueRowDurationLabel(225))
        assertEquals("1:02:03", queueRowDurationLabel(3_723))
        assertNull(queueRowDurationLabel(null), "unknown length shows no time column")
        assertNull(queueRowDurationLabel(0), "a zero duration is not a real length")
        assertNull(queueRowDurationLabel(-5))
    }

    /* -------- helpers ---------------------------------------------------- */

    /**
     * The placement rule the composables implement, in one place: the player
     * fills the box and is translated by [RelatedSheetMotion.playerOffsetY];
     * the sheet is bottom-aligned in that same box at a height equal to the
     * travel and translated by [RelatedSheetMotion.sheetOffsetY]. Returns the
     * player's lower boundary and the sheet's top edge.
     */
    private fun playerAndSheetEdges(availablePx: Float, travelPx: Float, progress: Float): Pair<Float, Float> {
        val motion = relatedSheetMotion(progress, travelPx)
        return (availablePx + motion.playerOffsetY) to (availablePx - travelPx + motion.sheetOffsetY)
    }

    /** Positive = empty gap between the two, negative = the sheet over the player. */
    private fun seamGapAt(availablePx: Float, travelPx: Float, progress: Float): Float {
        val (playerBottom, sheetTop) = playerAndSheetEdges(availablePx, travelPx, progress)
        return sheetTop - playerBottom
    }
}
