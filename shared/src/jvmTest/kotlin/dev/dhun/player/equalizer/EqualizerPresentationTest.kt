package dev.dhun.player.equalizer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EqualizerPresentationTest {

    @Test
    fun uiModelExposesTenLabeledBandsAndStockPlusCustomPresets() {
        val model = EqualizerState().toUiModel()
        assertFalse(model.enabled)
        assertEquals("flat", model.selectedPresetId)
        assertFalse(model.isCustom)
        assertEquals(EqualizerBands.COUNT, model.bands.size)
        assertEquals("60 Hz", model.bands.first().label)
        assertEquals("16 kHz", model.bands.last().label)
        model.bands.forEachIndexed { index, band ->
            assertEquals(index, band.index)
            assertEquals(EqualizerBands.FREQUENCIES_HZ[index], band.frequencyHz)
            assertEquals(0f, band.gainDb)
        }
        assertEquals(EqualizerPresets.ALL.size + 1, model.presets.size)
        assertEquals(1, model.presets.count { it.selected })
        assertTrue(model.presets.first { it.id == "flat" }.selected)
        assertEquals("Custom", model.presets.last().displayName)
        assertEquals(EqualizerPresets.CUSTOM_ID, model.presets.last().id)
        assertFalse(model.presets.last().selected)
        assertEquals(EqualizerBands.MIN_GAIN_DB, model.minGainDb)
        assertEquals(EqualizerBands.MAX_GAIN_DB, model.maxGainDb)
    }

    @Test
    fun customSelectionHighlightsOnlyTheCustomRow() {
        val session = EqualizerSession()
        session.selectPreset("techno")
        session.setBandGain(9, 0f)
        val model = session.current.toUiModel()
        assertTrue(model.isCustom)
        assertEquals(EqualizerPresets.CUSTOM_ID, model.selectedPresetId)
        assertEquals(1, model.presets.count { it.selected })
        assertTrue(model.presets.last().selected)
        assertFalse(model.presets.any { it.id == "techno" && it.selected })
        assertEquals(0f, model.bands.last().gainDb)
        assertEquals(8.0f, model.bands.first().gainDb)
        assertEquals(5f, model.preampDb)
    }

    @Test
    fun enabledFlagPassesThrough() {
        val on = EqualizerState(enabled = true).toUiModel()
        assertTrue(on.enabled)
        val off = EqualizerState(enabled = false).toUiModel()
        assertFalse(off.enabled)
    }
}
