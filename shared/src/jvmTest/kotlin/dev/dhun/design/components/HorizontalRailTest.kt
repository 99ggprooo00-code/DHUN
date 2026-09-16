package dev.dhun.design.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Geometry of the horizontal rail's glassy scrollbar — the numbers that decide
 * whether a Windows user can see and grab the rest of a rail. Pure functions,
 * so they are pinned here instead of eyeballed at one window size.
 *
 * The behaviour these support (and the Compose Multiplatform 1.8.2 facts behind
 * it) are documented on [DhunHorizontalRail].
 */
class HorizontalRailTest {

    @Test
    fun aRailThatFitsItsWindowGetsNoScrollbar() {
        // Six chips in a wide window: nothing is off-screen, so no thumb and —
        // in the composable — no reserved space under the rail.
        assertNull(RailScrollbarGeometry.thumb(contentExtentPx = 600f, viewportPx = 900f, scrollOffsetPx = 0f))
        // Sub-pixel noise must not raise one either.
        assertNull(RailScrollbarGeometry.thumb(contentExtentPx = 900.4f, viewportPx = 900f, scrollOffsetPx = 0f))
    }

    @Test
    fun thumbWidthTracksHowMuchIsOffScreen() {
        // A rail twice its viewport shows half of itself.
        val half = RailScrollbarGeometry.thumb(2000f, 1000f, 0f)
        assertEquals(0.5f, half!!.fraction, 0.0001f)
        assertEquals(0f, half.offsetFraction, 0.0001f)

        // …and scrolled to the end, the thumb sits at the end of the track.
        val atEnd = RailScrollbarGeometry.thumb(2000f, 1000f, 1000f)!!
        assertEquals(0.5f, atEnd.fraction, 0.0001f)
        assertEquals(1f, atEnd.offsetFraction, 0.0001f)

        // Halfway along, the thumb is centred.
        val middle = RailScrollbarGeometry.thumb(2000f, 1000f, 500f)!!
        assertEquals(0.5f, middle.offsetFraction, 0.0001f)
    }

    @Test
    fun thumbFractionIsFlooredSoItStaysGrabable() {
        // 40 albums in a phone-wide rail would be a 2.5% sliver otherwise.
        val thumb = RailScrollbarGeometry.thumb(40_000f, 1000f, 0f)!!
        assertEquals(RailScrollbarGeometry.MIN_THUMB_FRACTION, thumb.fraction, 0.0001f)
        assertTrue(thumb.fraction > 0.1f)
    }

    @Test
    fun thumbOffsetIsClampedToTheTrack() {
        // Overscroll / stale metrics must not park the thumb off the track.
        assertEquals(0f, RailScrollbarGeometry.thumb(2000f, 1000f, -300f)!!.offsetFraction, 0.0001f)
        assertEquals(1f, RailScrollbarGeometry.thumb(2000f, 1000f, 5000f)!!.offsetFraction, 0.0001f)
    }

    @Test
    fun degenerateMeasurementsProduceNoScrollbar() {
        assertNull(RailScrollbarGeometry.thumb(0f, 1000f, 0f))
        assertNull(RailScrollbarGeometry.thumb(-100f, 1000f, 0f))
        assertNull(RailScrollbarGeometry.thumb(2000f, 0f, 0f))
        assertNull(RailScrollbarGeometry.thumb(Float.NaN, 1000f, 0f))
        assertNull(RailScrollbarGeometry.thumb(2000f, Float.POSITIVE_INFINITY, 0f))
    }

    @Test
    fun oneTrackWidthOfDragCoversTheWholeContent() {
        // The thumb travels the track, so dragging it across the track must
        // cross the entire content extent — no multi-drag marathon.
        val delta = RailScrollbarGeometry.scrollDeltaFor(
            thumbDragPx = 900f,
            contentExtentPx = 4000f,
            trackWidthPx = 900f,
        )
        assertEquals(4000f, delta, 0.01f)

        // Half a track is half the content, and the sign follows the drag.
        assertEquals(
            -2000f,
            RailScrollbarGeometry.scrollDeltaFor(-450f, 4000f, 900f),
            0.01f,
        )
    }

    @Test
    fun aDragWithNothingToMeasureScrollsNothing() {
        assertEquals(0f, RailScrollbarGeometry.scrollDeltaFor(0f, 4000f, 900f), 0.0001f)
        assertEquals(0f, RailScrollbarGeometry.scrollDeltaFor(50f, 0f, 900f), 0.0001f)
        assertEquals(0f, RailScrollbarGeometry.scrollDeltaFor(50f, 4000f, 0f), 0.0001f)
        assertEquals(0f, RailScrollbarGeometry.scrollDeltaFor(Float.NaN, 4000f, 900f), 0.0001f)
    }

    @Test
    fun aSlowMouseReleaseDoesNotFling() {
        // Below the platform minimum the rail must stop dead — a click-drag
        // that already ended on the pointer-up frame is not a fling.
        assertTrue(!MouseRailFling.shouldFling(velocityPxPerSec = 40f, minimumFlingVelocity = 50f))
        assertTrue(!MouseRailFling.shouldFling(velocityPxPerSec = -40f, minimumFlingVelocity = 50f))
        assertTrue(!MouseRailFling.shouldFling(velocityPxPerSec = 0f, minimumFlingVelocity = 50f))
    }

    @Test
    fun aFastMouseReleaseDoesFlingInEitherDirection() {
        assertTrue(MouseRailFling.shouldFling(velocityPxPerSec = 800f, minimumFlingVelocity = 50f))
        assertTrue(MouseRailFling.shouldFling(velocityPxPerSec = -800f, minimumFlingVelocity = 50f))
        // Exactly at the floor still counts — otherwise a just-fast-enough
        // flick dies on a rounding edge.
        assertTrue(MouseRailFling.shouldFling(velocityPxPerSec = 50f, minimumFlingVelocity = 50f))
    }

    @Test
    fun garbageVelocitiesNeverFling() {
        assertTrue(!MouseRailFling.shouldFling(Float.NaN, 50f))
        assertTrue(!MouseRailFling.shouldFling(800f, Float.NaN))
        assertTrue(!MouseRailFling.shouldFling(Float.POSITIVE_INFINITY, 50f))
        assertTrue(!MouseRailFling.shouldFling(800f, -1f))
    }

    @Test
    fun contentCoastsTheWayThePointerWasMoving() {
        // Pointer right → content left, matching every drag delta in
        // dhunMouseDragScroll (`dispatchRawDelta(-dx)`).
        assertEquals(-800f, MouseRailFling.contentVelocityFromPointer(800f), 0.0001f)
        assertEquals(800f, MouseRailFling.contentVelocityFromPointer(-800f), 0.0001f)
        assertEquals(0f, MouseRailFling.contentVelocityFromPointer(0f), 0.0001f)
    }

    @Test
    fun decayStopsWhenTheRailCannotSwallowTheFrame() {
        assertTrue(MouseRailFling.shouldStopDecay(requestedDelta = 24f, consumedDelta = 0f))
        assertTrue(MouseRailFling.shouldStopDecay(requestedDelta = -24f, consumedDelta = -0.1f))
        assertTrue(!MouseRailFling.shouldStopDecay(requestedDelta = 24f, consumedDelta = 24f))
        assertTrue(!MouseRailFling.shouldStopDecay(requestedDelta = 24f, consumedDelta = 23.8f))
        assertTrue(MouseRailFling.shouldStopDecay(Float.NaN, 24f))
    }
}
