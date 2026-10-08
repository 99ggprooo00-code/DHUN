package dev.dhun.ui.shell

import kotlin.test.Test
import kotlin.test.assertEquals

class OfflineBannerPlacementTest {

    @Test
    fun `offline notice moves above FullPlayer instead of disappearing behind it`() {
        assertEquals(
            OfflineBannerPlacement.Hidden,
            DhunShellPolicy.offlineBannerPlacement(isOnline = true, fullPlayerVisible = true),
        )
        assertEquals(
            OfflineBannerPlacement.ScaffoldTopBar,
            DhunShellPolicy.offlineBannerPlacement(isOnline = false, fullPlayerVisible = false),
        )
        assertEquals(
            OfflineBannerPlacement.AboveFullPlayer,
            DhunShellPolicy.offlineBannerPlacement(isOnline = false, fullPlayerVisible = true),
        )
    }
}
