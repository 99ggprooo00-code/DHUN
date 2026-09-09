package dev.dhun.player.equalizer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EqualizerModelTest {

    @Test
    fun tenVlcBandsInLibVlcOrder() {
        assertEquals(10, EqualizerBands.COUNT)
        assertEquals(
            listOf(60, 170, 310, 600, 1_000, 3_000, 6_000, 12_000, 14_000, 16_000),
            EqualizerBands.FREQUENCIES_HZ,
        )
        assertEquals(EqualizerBands.COUNT, EqualizerBands.FREQUENCIES_HZ.size)
        assertEquals(EqualizerBands.COUNT, EqualizerBands.zeros().size)
        assertTrue(EqualizerBands.zeros().all { it == 0f })
    }

    @Test
    fun frequencyLabels() {
        assertEquals("60 Hz", EqualizerBands.frequencyLabel(60))
        assertEquals("310 Hz", EqualizerBands.frequencyLabel(310))
        assertEquals("1 kHz", EqualizerBands.frequencyLabel(1_000))
        assertEquals("3 kHz", EqualizerBands.frequencyLabel(3_000))
        assertEquals("16 kHz", EqualizerBands.frequencyLabel(16_000))
    }

    @Test
    fun clampGainRangeAndNonFinite() {
        assertEquals(-20f, EqualizerBands.clampGain(-99f))
        assertEquals(20f, EqualizerBands.clampGain(99f))
        assertEquals(3.5f, EqualizerBands.clampGain(3.5f))
        assertEquals(0f, EqualizerBands.clampGain(Float.NaN))
        assertEquals(0f, EqualizerBands.clampGain(Float.POSITIVE_INFINITY))
        assertEquals(0f, EqualizerBands.clampGain(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun clampGainsPadsAndTruncatesToTen() {
        assertEquals(EqualizerBands.zeros(), EqualizerBands.clampGains(emptyList()))
        assertEquals(
            listOf(1f, 2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            EqualizerBands.clampGains(listOf(1f, 2f)),
        )
        val long = List(15) { 5f }
        assertEquals(10, EqualizerBands.clampGains(long).size)
        assertTrue(EqualizerBands.clampGains(long).all { it == 5f })
        assertEquals(20f, EqualizerBands.clampGains(listOf(50f)).first())
    }

    @Test
    fun eighteenUniqueVlcPresets() {
        assertEquals(18, EqualizerPresets.ALL.size)
        assertEquals(18, EqualizerPresets.ALL.map { it.id }.toSet().size)
        assertEquals(18, EqualizerPresets.ALL.map { it.displayName }.toSet().size)
        EqualizerPresets.ALL.forEach { preset ->
            assertEquals(EqualizerBands.COUNT, preset.gainsDb.size, preset.id)
            preset.gainsDb.forEach { gain ->
                assertTrue(gain in EqualizerBands.MIN_GAIN_DB..EqualizerBands.MAX_GAIN_DB, preset.id)
            }
            assertTrue(preset.preampDb in EqualizerBands.MIN_GAIN_DB..EqualizerBands.MAX_GAIN_DB, preset.id)
        }
        assertEquals(EqualizerPresets.FLAT, EqualizerPresets.byId("flat"))
        assertEquals(EqualizerPresets.ROCK, EqualizerPresets.byId("rock"))
        assertNull(EqualizerPresets.byId("custom"))
        assertNull(EqualizerPresets.byId("nope"))
    }

    @Test
    fun flatIsZeroGainsWithVlcDefaultPreamp() {
        assertEquals(12f, EqualizerPresets.FLAT.preampDb)
        assertEquals(EqualizerBands.zeros(), EqualizerPresets.FLAT.gainsDb)
    }

    @Test
    fun rockCurveMatchesVlcEqualizerPresetsH() {
        // eqz_preset_10b "rock" in videolan/vlc modules/audio_filter/equalizer_presets.h
        assertEquals(5f, EqualizerPresets.ROCK.preampDb)
        assertEquals(
            listOf(8.0f, 4.8f, -5.6f, -8.0f, -3.2f, 4.0f, 8.8f, 11.2f, 11.2f, 11.2f),
            EqualizerPresets.ROCK.gainsDb,
        )
    }

    @Test
    fun matchingSnapsWithinEpsilonAndRejectsCustom() {
        assertEquals(EqualizerPresets.POP, EqualizerPresets.matching(EqualizerPresets.POP.gainsDb, 6f))
        assertEquals(
            EqualizerPresets.FLAT,
            EqualizerPresets.matching(EqualizerBands.zeros(), 12.02f),
        )
        assertNull(EqualizerPresets.matching(EqualizerBands.zeros(), 0f))
        val off = EqualizerPresets.ROCK.gainsDb.toMutableList()
        off[0] = 9.0f
        assertNull(EqualizerPresets.matching(off, EqualizerPresets.ROCK.preampDb))
    }

    @Test
    fun defaultStateIsDisabledFlat() {
        val state = EqualizerState()
        assertFalse(state.enabled)
        assertFalse(state.isCustom)
        assertEquals("flat", state.presetId)
        assertEquals(12f, state.preampDb)
        assertEquals(0f, state.band(0))
        assertEquals(0f, state.band(99))
    }

    @Test
    fun vlcCommandClampsAndKeepsCurveWhenDisabled() {
        val disabled = VlcEqualizerCommand.from(EqualizerState())
        assertFalse(disabled.enabled)
        assertEquals(12f, disabled.preampDb)
        assertEquals(EqualizerBands.COUNT, disabled.ampsDb.size)

        val wild = VlcEqualizerCommand.from(
            EqualizerState(
                enabled = true,
                presetId = EqualizerPresets.CUSTOM_ID,
                preampDb = 99f,
                gainsDb = listOf(-50f, 3f),
            ),
        )
        assertTrue(wild.enabled)
        assertEquals(20f, wild.preampDb)
        assertEquals(-20f, wild.ampsDb[0])
        assertEquals(3f, wild.ampsDb[1])
        assertEquals(10, wild.ampsDb.size)
    }
}
