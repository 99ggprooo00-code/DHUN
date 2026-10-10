package dev.dhun.android.playback

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Launch contract for API < 31: [DhunPlaybackService.onCreate] is bound
 * from [dev.dhun.android.MainActivity] on the main thread during the first
 * second. ActivityThread turns an onCreate throw into process death — the
 * activity fallback never runs. This test is the floor the user hit
 * (opens on Android 12 / API 31, dies on Android 10 / API 29).
 *
 * A session may be absent under Robolectric (no audio HAL); the contract
 * is only that onCreate returns.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 31])
class PlaybackServiceApi29LaunchTest {

    @Test
    fun `onCreate returns on API 29 and 31 without killing the process`() {
        val controller = Robolectric.buildService(DhunPlaybackService::class.java)
        controller.create()
        controller.destroy()
    }
}
