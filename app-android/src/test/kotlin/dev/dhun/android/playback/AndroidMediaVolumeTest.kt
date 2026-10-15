package dev.dhun.android.playback

import android.content.Context
import android.media.AudioManager
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.time.Duration
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidMediaVolumeTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val audio get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Test
    fun sliderWritesSystemMusicStreamAndReadsHardwareChanges() {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        assertTrue(max > 0)
        val volume = AndroidMediaVolume(context)
        volume.set(0.6f)
        assertEquals((0.6f * max).roundToInt(), audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) // hardware key / external mute
        assertEquals(0f, volume.current())
        volume.set(1f)
        assertEquals(max, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertEquals(1f, volume.current())
    }

    @Test
    fun playerVolumeFlowTracksSystemStreamEvenWhenPaused() {
        val item = MediaItem.Builder().setMediaId("audio").build()
        val engine = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.name) {
                "isPlaying", "isShuffleModeEnabled", "getPlayWhenReady" -> false
                "getPlaybackState" -> Player.STATE_READY
                "getMediaItemCount" -> 1
                "getCurrentMediaItemIndex" -> 0
                "getCurrentMediaItem", "getMediaItemAt" -> item
                "getRepeatMode" -> Player.REPEAT_MODE_OFF
                "getVolume" -> 1f
                "getCurrentPosition", "getDuration" -> 0L
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    java.lang.Float.TYPE -> 0f
                    else -> null
                }
            }
        } as Player
        val stream = AndroidMediaVolume(context)
        stream.set(1f)
        val player = AndroidDhunPlayer(engine, CoroutineScope(Dispatchers.Unconfined), mediaVolume = stream)
        try {
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1f, player.volume.value)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
            assertEquals(0f, player.volume.value)
            player.setVolume(1f)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1f, player.volume.value)
        } finally {
            player.release()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
