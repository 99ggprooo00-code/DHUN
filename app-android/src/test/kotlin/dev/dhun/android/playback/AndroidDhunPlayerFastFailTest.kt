package dev.dhun.android.playback

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dev.dhun.core.DhunError
import dev.dhun.core.PlaybackState
import dev.dhun.core.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/**
 * End-to-end-ish regression gate for the playback-diagnostics fix
 * (2026-09-09): with the player genuinely parked in STATE_BUFFERING and the
 * DI-wrapped provider having recorded a terminal (bot-gated) resolve
 * verdict, `AndroidDhunPlayer` must publish the typed `PlaybackState.Error`
 * once the buffering grace elapses — instead of letting the user stare at
 * "buffering" for the engine's multi-minute retry cascade.
 *
 * The engine double is a reflective `java.lang.reflect.Proxy` over the
 * media3 [Player] interface (only the getters AndroidDhunPlayer reads carry
 * real values; everything else returns type defaults), so no device, no
 * service and no engine are needed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidDhunPlayerFastFailTest {

    /** Reflective [Player] double; fields are the values refresh() observes. */
    private class PlayerDouble(val mediaId: String = "v1") {
        var playbackState: Int = Player.STATE_BUFFERING
        var isPlaying: Boolean = false
        private val item: MediaItem = MediaItem.Builder().setMediaId(mediaId).build()

        val player: Player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
            InvocationHandler { _, method, args ->
                dispatch(method.name, method.returnType, args)
            },
        ) as Player

        private fun dispatch(name: String, returnType: Class<*>, @Suppress("UNUSED_PARAMETER") args: Array<Any?>?): Any? =
            when (name) {
                "isPlaying" -> isPlaying
                "playbackState" -> playbackState
                "currentMediaItem" -> item
                "mediaItemCount" -> 1
                "getMediaItemAt" -> item
                "currentMediaItemIndex" -> 0
                "repeatMode" -> Player.REPEAT_MODE_OFF
                "shuffleModeEnabled" -> false
                "volume" -> 1f
                "playerError" -> null
                "currentPosition" -> 0L
                "duration" -> 0L
                "hasNextMediaItem" -> false
                "hasPreviousMediaItem" -> false
                "playWhenReady" -> false
                else -> defaultValue(returnType)
            }

        /** Unhandled members answer with harmless type defaults (void/Object → null). */
        private fun defaultValue(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Character.TYPE -> ' '
            else -> null
        }
    }

    private lateinit var engine: PlayerDouble
    private lateinit var outcomes: ResolveOutcomeLog
    private var now: Long = 1_000_000L
    private lateinit var player: AndroidDhunPlayer
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setUp() {
        engine = PlayerDouble()
        outcomes = ResolveOutcomeLog(clock = { now }, logger = { })
        player = AndroidDhunPlayer(
            player = engine.player,
            scope = scope,
            streamCache = null,
            resolveOutcomes = outcomes,
            clock = { now },
        )
        drain()
    }

    @After
    fun tearDown() {
        player.release()
        drain()
    }

    /** Runs posted main-looper work regardless of whether calls were inline. */
    private fun drain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Any public API that triggers refresh() on a non-buffering path works; this one is side-effect free on the double. */
    private fun refreshViaApi() {
        player.setRepeatMode(RepeatMode.OFF)
        drain()
    }

    @Test
    fun `terminal resolve verdict fast-fails buffering into the typed error`() {
        outcomes.record(
            "v1",
            DhunError.AuthRequired(detail = "web_remix=AUTH_REQUIRED(Sign in to confirm you're not a bot)"),
        )
        now += FAST_FAIL_GRACE_MS + 2_000 // the buffering bout started at construction
        refreshViaApi()

        val state = player.state.value
        assertTrue("expected Error, was $state", state is PlaybackState.Error)
        state as PlaybackState.Error
        assertEquals("This content needs a signed-in session.", state.message)
        assertEquals("web_remix=AUTH_REQUIRED(Sign in to confirm you're not a bot)", state.detail)
    }

    @Test
    fun `no verdict keeps buffering honestly`() {
        now += FAST_FAIL_GRACE_MS + 2_000
        refreshViaApi()
        assertTrue(player.state.value is PlaybackState.Buffering)
    }

    @Test
    fun `grace keeps a young terminal verdict buffering`() {
        outcomes.record("v1", DhunError.AuthRequired(detail = "gated"))
        now += 1_000 // far inside the grace
        refreshViaApi()
        assertTrue(player.state.value is PlaybackState.Buffering)
    }

    @Test
    fun `retry clears the verdict so the next attempt is judged fresh`() {
        outcomes.record("v1", DhunError.AuthRequired(detail = "gated"))
        now += FAST_FAIL_GRACE_MS + 2_000
        refreshViaApi()
        assertTrue(player.state.value is PlaybackState.Error)

        player.retry()
        drain()
        assertTrue("expected Buffering after retry, was ${player.state.value}", player.state.value is PlaybackState.Buffering)
    }

    @Test
    fun `a track that starts playing is never fast-failed`() {
        // Offline cache-span replay: terminal verdict on file, but spans serve.
        outcomes.record("v1", DhunError.AuthRequired(detail = "gated"))
        now += FAST_FAIL_GRACE_MS + 2_000
        engine.isPlaying = true
        engine.playbackState = Player.STATE_READY
        refreshViaApi()
        assertTrue(player.state.value is PlaybackState.Playing)
    }
}
