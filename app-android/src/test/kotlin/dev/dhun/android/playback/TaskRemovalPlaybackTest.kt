package dev.dhun.android.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class TaskRemovalPlaybackTest {
    @Test
    fun dismissalSilencesPlayerBeforeStoppingServiceWithoutClearingQueue() {
        val calls = mutableListOf<String>()
        val player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, _ ->
            // No isPlaying guard: buffering and paused players must stop too.
            calls += method.name
            null
        } as Player
        stopPlaybackOnTaskRemoval(player) { calls += "stopService" }
        assertEquals(listOf("pause", "stop", "stopService"), calls)
    }

    @Test
    fun dismissalBeforeSessionCreationStillStopsService() {
        var stops = 0
        stopPlaybackOnTaskRemoval(null) { stops++ }
        assertEquals(1, stops)
    }

    @Test
    fun serviceShutdownIsRequestedEvenIfEngineStopThrows() {
        var stoppedService = false
        val player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, _ ->
            if (method.name == "stop") error("engine already released")
            null
        } as Player
        val failure = runCatching {
            stopPlaybackOnTaskRemoval(player) { stoppedService = true }
        }
        assertEquals(true, failure.isFailure)
        assertEquals(true, stoppedService)
    }
}
