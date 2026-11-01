package dev.dhun.legal

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DhunAppInfoTest {

    @Test
    fun `only the explicit beta release channel unlocks test-only surfaces`() {
        assertTrue(appInfo(DhunAppInfo.BETA_RELEASE_CHANNEL).isBetaRelease)
        assertTrue(appInfo("Beta Release").isBetaRelease)
        assertFalse(appInfo("public pre-release").isBetaRelease)
        assertFalse(appInfo("debug build").isBetaRelease)
        assertFalse(DhunAppInfo.Unknown.isBetaRelease)
    }

    private fun appInfo(channel: String) = DhunAppInfo(
        versionName = "1.00.001",
        versionCode = "6",
        releaseChannel = channel,
        platform = "test",
    )
}
