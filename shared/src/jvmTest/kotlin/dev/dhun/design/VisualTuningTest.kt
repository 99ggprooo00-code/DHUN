package dev.dhun.design

import dev.dhun.data.SettingsKeys
import dev.dhun.design.components.readingSurfaceFill
import dev.dhun.design.components.contentRowFill
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import androidx.compose.ui.graphics.Color

class VisualTuningTest {
    @BeforeTest fun resetBefore() = DhunVisualTuning.restore(emptyMap(), enabled = false)
    @AfterTest fun reset() = DhunVisualTuning.restore(emptyMap(), enabled = false)

    @Test
    fun allSlidersHaveIndependentKeysAndFiftyIsTheExactOriginal() {
        assertEquals(VisualPart.entries.size, VisualPart.entries.map { it.key }.distinct().size)
        VisualPart.entries.forEach { part ->
            assertTrue(part.key in SettingsKeys.all, "${part.label} must reset with settings")
            assertEquals(50, DhunVisualTuning.value(part))
            assertEquals(0.62f, DhunVisualTuning.alpha(part, 0.62f, 0.50f, 0.74f))
        }
    }

    @Test
    fun liveValuesAreClampedAndOnlyAffectTheirOwnPart() {
        DhunVisualTuning.set(VisualPart.LIST_ROWS, 1000)
        assertEquals(100, DhunVisualTuning.value(VisualPart.LIST_ROWS))
        assertEquals(0.74f, DhunVisualTuning.alpha(VisualPart.LIST_ROWS, 0.62f, 0.50f, 0.74f))
        assertEquals(0.62f, DhunVisualTuning.alpha(VisualPart.QUEUE_ROWS, 0.62f, 0.50f, 0.74f))
        DhunVisualTuning.set(VisualPart.LIST_ROWS, -20)
        assertEquals(0, DhunVisualTuning.value(VisualPart.LIST_ROWS))
        assertEquals(0.50f, DhunVisualTuning.alpha(VisualPart.LIST_ROWS, 0.62f, 0.50f, 0.74f))
        assertEquals(0f, DhunVisualTuning.veilAlpha(VisualPart.LIST_ROWS, 0f))
    }

    @Test
    fun retiredPartsIgnoreSavedValuesAndCannotBeChanged() {
        DhunVisualTuning.restore(mapOf(VisualPart.MINI_PLAYER to 100, VisualPart.NAVIGATION to 83), enabled = true)
        DhunVisualTuning.set(VisualPart.PAGE_BACKDROP, 0)
        DhunVisualTuning.set(VisualPart.MINI_PLAYER, 100)
        assertEquals(50, DhunVisualTuning.value(VisualPart.PAGE_BACKDROP))
        assertEquals(0, DhunVisualTuning.value(VisualPart.MINI_PLAYER))
        assertEquals(0, DhunVisualTuning.value(VisualPart.NAVIGATION))
        DhunVisualTuning.restore(emptyMap(), enabled = false)
        assertEquals(50, DhunVisualTuning.value(VisualPart.MINI_PLAYER))
    }

    @Test
    fun tonalSliderIsLiveTestOnlyAndRespectsOpaqueDialogs() {
        val dark = Color(0xFF303030)
        assertEquals(dark, DhunVisualTuning.tonalSurface(dark, false))
        DhunVisualTuning.restore(emptyMap(), enabled = true)
        assertEquals(0.76f, DhunVisualTuning.tonalSurface(dark, false).alpha, 0.01f)
        assertEquals(0.82f, DhunVisualTuning.tonalSurface(dark, true).alpha, 0.01f)
        assertEquals(1f, DhunAppearance.tokens.surfaceVariant.alpha, "dialog base stays opaque")
        DhunVisualTuning.set(VisualPart.TONAL_SURFACES, 0)
        assertEquals(0.52f, DhunVisualTuning.tonalSurface(dark, false).alpha, 0.01f)
        DhunVisualTuning.set(VisualPart.TONAL_SURFACES, 100)
        assertEquals(dark, DhunVisualTuning.tonalSurface(dark, false))
        DhunVisualTuning.restore(mapOf(VisualPart.TONAL_SURFACES to 0), enabled = false)
        assertEquals(dark, DhunVisualTuning.tonalSurface(dark, false))
    }

    @Test
    fun blackSettingsCardsRespondToTonalSliderWithoutChangingQueue() {
        DhunAppearance.setAppearance(nextMode = DhunThemeMode.BLACK)
        try {
            DhunVisualTuning.restore(emptyMap(), enabled = true)
            val original = readingSurfaceFill()
            assertEquals(0.88f * 0.76f, original.alpha, 0.02f)
            DhunVisualTuning.set(VisualPart.TONAL_SURFACES, 0)
            assertTrue(readingSurfaceFill().alpha < original.alpha)
            assertTrue(contentRowFill().alpha < original.alpha)
            assertEquals(50, DhunVisualTuning.value(VisualPart.QUEUE_SHEET))
            assertEquals(50, DhunVisualTuning.value(VisualPart.QUEUE_ROWS))
            DhunVisualTuning.restore(emptyMap(), enabled = false)
            assertEquals(0.88f, readingSurfaceFill().alpha, 0.02f)
        } finally {
            DhunAppearance.reset()
        }
    }

    @Test
    fun publicBuildIgnoresSavedTestSettingsAndResetRestoresMidpoint() {
        val saved = mapOf(VisualPart.QUEUE_SHEET to 90, VisualPart.PAGE_BACKDROP to 20)
        DhunVisualTuning.restore(saved, enabled = false)
        assertEquals(50, DhunVisualTuning.value(VisualPart.QUEUE_SHEET))
        DhunVisualTuning.restore(saved, enabled = true)
        assertEquals(90, DhunVisualTuning.value(VisualPart.QUEUE_SHEET))
        assertEquals(50, DhunVisualTuning.value(VisualPart.PAGE_BACKDROP))
        assertEquals(0, DhunVisualTuning.value(VisualPart.MINI_PLAYER))
        assertEquals(0, DhunVisualTuning.value(VisualPart.NAVIGATION))
        DhunVisualTuning.reset()
        assertEquals(50, DhunVisualTuning.value(VisualPart.QUEUE_SHEET))
        assertEquals(0, DhunVisualTuning.value(VisualPart.MINI_PLAYER))
    }
}
