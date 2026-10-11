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
