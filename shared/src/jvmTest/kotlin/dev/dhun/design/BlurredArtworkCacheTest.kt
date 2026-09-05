package dev.dhun.design

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ADR-002 P4: once-per-track key contract — no continuous reblur. */
class BlurredArtworkCacheTest {

    @BeforeTest
    fun reset() {
        BlurredArtworkCache.clear()
    }

    @Test
    fun keyPrefersThumbnailUrlOverId() {
        assertEquals(
            "https://i.ytimg.com/x.jpg",
            BlurredArtworkCache.keyFor("https://i.ytimg.com/x.jpg", "vid1"),
        )
        assertEquals("vid1", BlurredArtworkCache.keyFor(null, "vid1"))
        assertEquals("vid1", BlurredArtworkCache.keyFor("  ", "vid1"))
        assertEquals("", BlurredArtworkCache.keyFor(null, null))
    }

    @Test
    fun markPreparedIsIdempotentPerKey() {
        assertFalse(BlurredArtworkCache.isPrepared("a"))
        BlurredArtworkCache.markPrepared("a")
        assertTrue(BlurredArtworkCache.isPrepared("a"))
        BlurredArtworkCache.markPrepared("a")
        assertTrue(BlurredArtworkCache.isPrepared("a"))
        BlurredArtworkCache.markPrepared("b")
        assertFalse(BlurredArtworkCache.isPrepared("a"))
        assertTrue(BlurredArtworkCache.isPrepared("b"))
    }

    @Test
    fun blankKeyDoesNotClobber() {
        BlurredArtworkCache.markPrepared("track-1")
        BlurredArtworkCache.markPrepared("")
        assertTrue(BlurredArtworkCache.isPrepared("track-1"))
    }
}
