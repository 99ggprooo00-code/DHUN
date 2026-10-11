package dev.dhun.design

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises the global token readers while a real recomposer is active. */
class LiveThemeCompositionTest {
    @Test
    fun paletteSwitchesDuringCompositionWithoutPublishingPartialTokens() = runBlocking {
        DhunAppearance.reset()
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + clock)
        val composition = Composition(object : AbstractApplier<Unit>(Unit) {
            override fun insertTopDown(index: Int, instance: Unit) = Unit
            override fun insertBottomUp(index: Int, instance: Unit) = Unit
            override fun remove(index: Int, count: Int) = Unit
            override fun move(from: Int, to: Int, count: Int) = Unit
            override fun onClear() = Unit
        }, recomposer)
        val runner = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        var frames = 0
        val seen = mutableListOf<DhunTokens>()
        try {
            composition.setContent {
                val tokens = DhunAppearance.tokens
                assertEquals(tokens.accent, DhunColors.accent)
                assertEquals(tokens.background, DhunColors.background)
                seen += tokens
                frames++
            }
            withTimeout(3_000) { while (frames == 0) { clock.sendFrame(1L); delay(10) } }
            for (mode in listOf(DhunThemeMode.LIGHT, DhunThemeMode.DARK, DhunThemeMode.LIGHT)) {
                for (accent in DhunAccent.entries) {
                    DhunAppearance.setAppearance(mode, accent)
                    androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
                    clock.sendFrame(frames.toLong() + 1)
                    delay(20)
                    assertEquals(mode == DhunThemeMode.LIGHT, DhunAppearance.tokens.isLight)
                }
            }
            withTimeout(3_000) {
                while (seen.last() != DhunAppearance.tokens) {
                    androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
                    clock.sendFrame(frames.toLong() + 1)
                    delay(10)
                }
            }
            assertTrue(seen.size > 1)
        } finally {
            composition.dispose()
            recomposer.cancel()
            runner.join()
            DhunAppearance.reset()
        }
    }
}
