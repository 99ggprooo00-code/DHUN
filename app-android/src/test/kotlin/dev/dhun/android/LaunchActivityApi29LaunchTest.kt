package dev.dhun.android

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * API 29 (Android 10) launcher must be a raw [Activity] that paints the
 * View connecting screen. Robolectric is not ART verification, but it
 * does catch a missing layout / wrong superclass before a device does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class LaunchActivityApi29LaunchTest {

    @Test
    fun `LaunchActivity is a framework Activity, not ComponentActivity`() {
        assertEquals(Activity::class.java, LaunchActivity::class.java.superclass)
    }

    @Test
    fun `onCreate inflates the View connecting screen`() {
        val activity = Robolectric.buildActivity(LaunchActivity::class.java).create().get()
        assertNotNull(activity.findViewById(R.id.connecting_brand))
        assertNotNull(activity.findViewById(R.id.connecting_progress))
        assertNotNull(activity.findViewById(R.id.connecting_status))
    }
}
