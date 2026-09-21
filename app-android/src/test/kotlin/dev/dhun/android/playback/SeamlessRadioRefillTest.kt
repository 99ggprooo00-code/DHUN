package dev.dhun.android.playback

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dev.dhun.core.PlaybackState
import dev.dhun.core.RepeatMode
import dev.dhun.core.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
 * Endless-radio engine regression (2026-09-21). The refill hands the next
 * /next page to [AndroidDhunPlayer.replaceQueueKeepingCurrent]; this test
 * pins the engine-level contract that makes the refill seamless: the
 * SOUNDING MediaItem instance is never replaced, re-prepared or re-buffered.
 * The tail is trimmed around it and the new page is appended behind it.
 *
 * A regression that rebuilt the timeline (setMediaItems + prepare) would
 * restart the current song: an audible gap, exactly what endless radio must
 * not do. The counters below fail on that, not just on final order.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeamlessRadioRefillTest {

    /**
     * Reflective [Player] double with a REAL mutable timeline: every
     * append/remove/prepare/set is recorded. Mirrors Media3's index
     * bookkeeping for removals around the current item (the current item
     * survives a trim; only its index shifts).
     */
    private class TimelineDouble {
        val items = mutableListOf<MediaItem>()
        var currentIndex = 0
        var isPlaying = true
        var playbackState = Player.STATE_READY
        var prepareCalls = 0
        var setMediaItemsCalls = 0
        val removedRanges = mutableListOf<Pair<Int, Int>>()
        val addedItems = mutableListOf<MediaItem>()
        val seekToCalls = mutableListOf<Long>()

        val player: Player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
            InvocationHandler { _, method, args ->
                dispatch(method.name, method.returnType, args)
            },
        ) as Player

        /** Non-null arg capture; null (zero-arg member) falls through. */
        private fun arg(args: Array<out Any?>?, i: Int): Any? = args?.getOrNull(i)

        private fun dispatch(name: String, returnType: Class<*>, args: Array<out Any?>?): Any? {
            // ---- getters (the values refresh() observes) ----
            when (name) {
                "isPlaying" -> return isPlaying
                "getPlaybackState" -> return playbackState
                "getCurrentMediaItem" -> return items.getOrNull(currentIndex)
                "getMediaItemCount" -> return items.size
                "getMediaItemAt" -> return items[arg(args, 0) as Int]
                "getCurrentMediaItemIndex" -> return currentIndex
                "getRepeatMode" -> return Player.REPEAT_MODE_OFF
                "isShuffleModeEnabled" -> return false
                "getVolume" -> return 1f
                "getPlayerError" -> return null
                "getCurrentPosition" -> return 42_000L
                "getDuration" -> return 200_000L
                "hasNextMediaItem" -> return currentIndex + 1 < items.size
                "hasPreviousMediaItem" -> return currentIndex > 0
                "getPlayWhenReady" -> return true
            }
            // ---- timeline mutations (recorded) ----
            when (name) {
                "setMediaItems" -> {
                    setMediaItemsCalls++
                    items.clear()
                    items += arg(args, 0) as List<MediaItem>
                    currentIndex = arg(args, 1) as Int
                    return null
                }
                "prepare" -> {
                    prepareCalls++
                    return null
                }
                "addMediaItems" -> when {
                    args == null || args.size == 1 -> {
                        val list = arg(args, 0) as List<MediaItem>
                        addedItems += list
                        items += list
                    }
                    else -> {
                        val at = arg(args, 0) as Int
                        val list = arg(args, 1) as List<MediaItem>
                        addedItems += list
                        items.addAll(at, list)
                        if (currentIndex >= at) currentIndex += list.size
                    }
                }
                "addMediaItem" -> when {
                    args == null || args.size == 1 -> {
                        val item = arg(args, 0) as MediaItem
                        addedItems += item
                        items += item
                    }
                    else -> {
                        val at = arg(args, 0) as Int
                        val item = arg(args, 1) as MediaItem
                        addedItems += item
                        items.add(at, item)
                        if (currentIndex >= at) currentIndex++
                    }
                }
                "removeMediaItems" -> {
                    val from = arg(args, 0) as Int
                    val end = arg(args, 1) as Int
                    removedRanges += from to end
                    items.subList(from, end).clear()
                    // Media3 keeps the CURRENT item across a trim; only its
                    // index shifts.
                    currentIndex = when {
                        currentIndex >= end -> (currentIndex - (end - from)).coerceAtLeast(0)
                        currentIndex >= from -> (end - 1).coerceAtLeast(0)
                        else -> currentIndex
                    }
                    return null
                }
                "seekTo" -> {
                    if (args != null && args.size == 1) {
                        seekToCalls += (arg(args, 0) as? Long) ?: 0L
                    }
                    return null
                }
            }
            // ---- no-op setters / lifecycle ----
            when (name) {
                "setPlayWhenReady", "setShuffleModeEnabled", "setRepeatMode", "setVolume",
                "play", "pause", "stop", "release", "addListener", "removeListener" -> return null
            }
            return defaultValue(returnType)
        }

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

    private lateinit var double: TimelineDouble
    private lateinit var player: AndroidDhunPlayer
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setUp() {
        double = TimelineDouble()
        player = AndroidDhunPlayer(
            player = double.player,
            scope = scope,
            streamCache = null,
            resolveOutcomes = ResolveOutcomeLog(clock = { System.currentTimeMillis() }, logger = { }),
            clock = { System.currentTimeMillis() },
        )
        drain()
    }

    @After
    fun tearDown() {
        player.release()
        drain()
    }

    private fun drain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Runs a suspend engine call, pumping the main looper until it lands. */
    private fun pump(block: suspend () -> Unit) {
        val job = CoroutineScope(Dispatchers.Default).launch(start = CoroutineStart.UNDISPATCHED) { block() }
        val deadline = System.currentTimeMillis() + 15_000
        while (!job.isCompleted) {
            drain()
            if (System.currentTimeMillis() > deadline) error("main-queue pump timed out")
            Thread.sleep(10)
        }
    }

    private fun track(id: String) =
        Track(id = id, title = "Song $id", artistName = "Artist $id", durationSeconds = 200)

    @Test
    fun `radio refill swaps the tail without touching the sounding item`() {
        val queue = (1..7).map { track("t$it") }
        pump { player.prepareQueue(queue, 0) }
        drain()
        assertEquals(7, double.items.size)
        assertEquals(1, double.prepareCalls)
        assertEquals(1, double.setMediaItemsCalls)

        // Natural advance to t4 (index 3): the engine moves its cursor
        // itself; a refresh reconciles the bookkeeper.
        double.currentIndex = 3
        player.setRepeatMode(RepeatMode.OFF)
        drain()
        assertEquals(3, player.currentQueueIndex.value)
        assertEquals("t4", player.currentTrack.value?.id)
        assertTrue(player.state.value is PlaybackState.Playing)

        val soundingBefore = double.items[3]

        // The endless-radio refill: the next /next page swaps in behind t4.
        val radio = (100..124).map { track("r$it") }
        pump { player.replaceQueueKeepingCurrent(radio) }
        drain()

        // THE contract: the sounding MediaItem instance is literally
        // untouched. No re-prepare, no rebuild, no re-buffer, no seek.
        // (Media3 trims around it, so after the swap it sits at index 0.)
        assertSame("sounding item must keep its exact instance", soundingBefore, double.items[0])
        assertEquals(1, double.prepareCalls, "no re-prepare (would re-buffer = gap)")
        assertEquals(1, double.setMediaItemsCalls, "no timeline rebuild")
        assertTrue(double.seekToCalls.isEmpty(), "no seek, same position")
        assertEquals(listOf(4 to 7, 0 to 3), double.removedRanges, "trim around the head, then append")

        // Tail swapped around the head: the played prefix and the old tail
        // are gone, the station page sits behind the current song.
        assertEquals(
            listOf("t4") + (100..124).map { "r$it" },
            double.items.map { it.mediaId },
        )
        assertEquals(0, double.currentIndex)
        assertEquals(25, double.addedItems.size, "the new page is appended as one batch")

        // Flows agree with the engine: queue tab, highlight, state.
        assertEquals(
            (listOf("t4") + (100..124).map { "r$it" }),
            player.queue.value.map { it.id },
        )
        assertEquals(0, player.currentQueueIndex.value)
        assertEquals("t4", player.currentTrack.value?.id)
        assertTrue(player.state.value is PlaybackState.Playing)
    }
}
