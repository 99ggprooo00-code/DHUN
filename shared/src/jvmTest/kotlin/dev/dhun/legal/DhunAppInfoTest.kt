package dev.dhun.legal

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DhunAppInfoTest {

    @Test
    fun `only the explicit test release channel unlocks test-only surfaces`() {
        assertTrue(appInfo(DhunAppInfo.TEST_RELEASE_CHANNEL).isTestRelease)
        assertTrue(appInfo("Test Release").isTestRelease)
        assertFalse(appInfo("public pre-release").isTestRelease)
        assertFalse(appInfo("debug build").isTestRelease)
        assertFalse(DhunAppInfo.Unknown.isTestRelease)
    }

    private fun appInfo(channel: String) = DhunAppInfo(
        versionName = "1.00.001",
        versionCode = "6",
        releaseChannel = channel,
        platform = "test",
    )
}
