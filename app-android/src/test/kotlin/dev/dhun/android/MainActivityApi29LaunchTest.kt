package dev.dhun.android

import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * API 29 (Android 10) must paint a View connecting screen from onCreate.
 * Compose is deferred until the engine is Ready so a GraphicsLayer /
 * RenderEffect class-load cannot make launch look like a no-op.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class MainActivityApi29LaunchTest {

    @Test
    fun `onCreate inflates the View connecting screen`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        assertNotNull(activity.findViewById(R.id.connecting_brand))
        assertNotNull(activity.findViewById(R.id.connecting_progress))
    }
}
