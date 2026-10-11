package dev.dhun.desktop

import androidx.compose.runtime.snapshots.SnapshotStateObserver
import dev.dhun.design.DhunAccent
import dev.dhun.design.DhunAppearance
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Desktop's two-pane token reads are observed while the accent/theme changes. */
class LiveAppearanceTest {
    @Test
    fun paletteAndMaterialSchemeAreSafeUnderConcurrentDesktopReads() {
        DhunAppearance.reset()
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        val reader = Thread {
            try {
                repeat(2_000) {
                    val palette = DhunAppearance.tokens
                    // First access to a token set's lazy scheme may race with
                    // Skiko's paint thread. Kotlin's synchronized lazy must
                    // publish the fully initialized scheme.
                    assertEquals(palette.accent, palette.materialScheme.primary)
                }
            } catch (t: Throwable) { failure.set(t) }
        }
        try {
            reader.start()
            repeat(200) { n ->
                DhunAppearance.setAppearance(
                    if (n % 2 == 0) DhunThemeMode.LIGHT else DhunThemeMode.DARK,
                    DhunAccent.entries[n % DhunAccent.entries.size],
                )
            }
            reader.join(5_000)
            assertTrue(!reader.isAlive, "background palette reader did not finish")
            failure.get()?.let { throw it }
        } finally { DhunAppearance.reset() }
    }

    @Test
    fun liveSwitchIsAtomicForSnapshotObservers() {
        DhunAppearance.reset()
        var invalid = false
        var notifications = 0
        val observer = SnapshotStateObserver { it() }
        observer.start()
        try {
            val scope = Any()
            fun observe() = observer.observeReads(scope, { _: Any -> notifications++ }) {
                val palette = DhunAppearance.tokens
                if (DhunColors.accent != palette.accent || DhunColors.surface != palette.surface) invalid = true
            }
            observe()
            for (mode in listOf(DhunThemeMode.DARK, DhunThemeMode.LIGHT)) {
                for (accent in DhunAccent.entries) {
                    DhunAppearance.setAppearance(mode, accent)
                    androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
                    observe()
                    assertEquals(mode == DhunThemeMode.LIGHT, DhunAppearance.tokens.isLight)
                }
            }
            assertTrue(!invalid)
            assertTrue(notifications > 0)
        } finally {
            observer.stop()
            observer.clear()
            DhunAppearance.reset()
        }
    }
}
