package dev.dhun.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Resolution tiers for artwork URLs — the Now Playing de-blur contract. */
class ArtworkUrlsTest {

    @Test
    fun upgradesProxySizeParamsToListTier() {
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w544-h544-l90-rj",
            ArtworkUrls.list("https://lh3.googleusercontent.com/abc=w60-h60-l90-rj"),
        )
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w544-h544-l90-rj",
            ArtworkUrls.list("https://lh3.googleusercontent.com/abc=w120-h120-l90-rj"),
        )
    }

    @Test
    fun upgradesArbitraryProxySizesNotJustW60W120() {
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w544-h544-l90-rj",
            ArtworkUrls.list("https://lh3.googleusercontent.com/abc=w176-h176-l90-rj"),
        )
    }

    @Test
    fun fixesProtocolRelativeUrls() {
        assertEquals(
            "https://yt3.ggpht.com/abc=w544-h544-l90-rj",
            ArtworkUrls.list("//yt3.ggpht.com/abc=w60-h60-l90-rj"),
        )
    }

    @Test
    fun upgradesAvatarSizeParamsPreservingCrop() {
        assertEquals(
            "https://lh3.googleusercontent.com/a/xyz=s544-c-k-c0x00ffffff-no-rj",
            ArtworkUrls.list("https://lh3.googleusercontent.com/a/xyz=s176-c-k-c0x00ffffff-no-rj"),
        )
    }

    @Test
    fun nowPlayingRequests1024Tier() {
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w1024-h1024-l90-rj",
            ArtworkUrls.nowPlaying("https://lh3.googleusercontent.com/abc=w60-h60-l90-rj"),
        )
    }

    @Test
    fun leavesYtimgHostsUntouched() {
        val url = "https://i.ytimg.com/vi/utwMHfDZ6SA/hqdefault.jpg"
        assertEquals(url, ArtworkUrls.list(url))
        assertEquals(url, ArtworkUrls.nowPlaying(url))
    }

    @Test
    fun nullAndBlankPassThrough() {
        assertNull(ArtworkUrls.list(null))
        assertNull(ArtworkUrls.nowPlaying(null))
    }
}
