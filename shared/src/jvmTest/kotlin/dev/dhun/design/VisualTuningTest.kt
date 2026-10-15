package dev.dhun.design

import dev.dhun.data.SettingsKeys
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VisualTuningTest {
    @BeforeTest fun resetBefore() = DhunVisualTuning.reset()
    @AfterTest fun reset() = DhunVisualTuning.reset()

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
    fun publicBuildIgnoresSavedTestSettingsAndResetRestoresMidpoint() {
        val saved = mapOf(VisualPart.QUEUE_SHEET to 90, VisualPart.PAGE_BACKDROP to 20)
        DhunVisualTuning.restore(saved, enabled = false)
        assertEquals(50, DhunVisualTuning.value(VisualPart.QUEUE_SHEET))
        DhunVisualTuning.restore(saved, enabled = true)
        assertEquals(90, DhunVisualTuning.value(VisualPart.QUEUE_SHEET))
        assertEquals(20, DhunVisualTuning.value(VisualPart.PAGE_BACKDROP))
        DhunVisualTuning.reset()
        assertEquals(50, DhunVisualTuning.value(VisualPart.QUEUE_SHEET))
    }
}
