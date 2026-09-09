package dev.dhun.player.equalizer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EqualizerSessionTest {

    private class RecordingEngine : EqualizerEngine {
        val history = mutableListOf<EqualizerState>()
        override fun apply(state: EqualizerState) {
            history += state
        }
    }

    @Test
    fun constructionAppliesInitialDisabledFlat() {
        val engine = RecordingEngine()
        val session = EqualizerSession(engine)
        assertEquals(1, engine.history.size)
        assertFalse(session.current.enabled)
        assertEquals("flat", session.current.presetId)
        assertEquals(EqualizerBands.zeros(), session.current.gainsDb)
    }

    @Test
    fun enableDoesNotWipeTheCurve() {
        val engine = RecordingEngine()
        val session = EqualizerSession(engine)
        session.selectPreset("rock")
        session.setEnabled(true)
        assertTrue(session.current.enabled)
        assertEquals("rock", session.current.presetId)
        assertEquals(EqualizerPresets.ROCK.gainsDb, session.current.gainsDb)
        session.setEnabled(false)
        assertFalse(session.current.enabled)
        assertEquals(EqualizerPresets.ROCK.gainsDb, session.current.gainsDb)
        assertTrue(engine.history.last().enabled.not())
    }

    @Test
    fun unknownPresetIsNoOp() {
        val engine = RecordingEngine()
        val session = EqualizerSession(engine)
        val before = engine.history.size
        session.selectPreset("nope")
        session.selectPreset(EqualizerPresets.CUSTOM_ID)
        assertEquals(before, engine.history.size)
        assertEquals("flat", session.current.presetId)
    }

    @Test
    fun movingABandMarksCustomAndSnapsBack() {
        val session = EqualizerSession()
        session.selectPreset("rock")
        session.setBandGain(0, 9.0f)
        assertTrue(session.current.isCustom)
        assertEquals(EqualizerPresets.CUSTOM_ID, session.current.presetId)
        assertEquals(9.0f, session.current.band(0))
        session.setBandGain(0, 8.0f)
        assertEquals("rock", session.current.presetId)
        assertFalse(session.current.isCustom)
    }

    @Test
    fun outOfRangeBandIndexIsNoOp() {
        val engine = RecordingEngine()
        val session = EqualizerSession(engine)
        val before = engine.history.size
        session.setBandGain(-1, 5f)
        session.setBandGain(10, 5f)
        assertEquals(before, engine.history.size)
    }

    @Test
    fun preampChangeMarksCustomUntilItMatchesAgain() {
        val session = EqualizerSession()
        session.selectPreset("pop")
        session.setPreamp(0f)
        assertTrue(session.current.isCustom)
        session.setPreamp(6f)
        assertEquals("pop", session.current.presetId)
    }

    @Test
    fun gainsAreClampedAndSameValueDoesNotReapply() {
        val engine = RecordingEngine()
        val session = EqualizerSession(engine)
        session.setBandGain(0, 50f)
        assertEquals(20f, session.current.band(0))
        val afterClamp = engine.history.size
        session.setBandGain(0, 99f)
        assertEquals(afterClamp, engine.history.size)
    }

    @Test
    fun setGainsShortListPadsAndResetRestoresFlat() {
        val session = EqualizerSession()
        session.setEnabled(true)
        session.setGains(listOf(1f, 2f, 3f))
        assertTrue(session.current.isCustom)
        assertEquals(listOf(1f, 2f, 3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), session.current.gainsDb)
        session.resetToFlat()
        assertEquals("flat", session.current.presetId)
        assertEquals(EqualizerBands.zeros(), session.current.gainsDb)
        assertEquals(12f, session.current.preampDb)
        assertTrue(session.current.enabled) // reset does not disable
    }

    @Test
    fun restoreNormalizesAndReapplyPushesWithoutChanging() {
        val engine = RecordingEngine()
        val session = EqualizerSession(engine)
        session.restore(
            EqualizerState(
                enabled = true,
                presetId = "garbage",
                preampDb = 5f,
                gainsDb = EqualizerPresets.ROCK.gainsDb,
            ),
        )
        assertEquals("rock", session.current.presetId)
        assertTrue(session.current.enabled)
        val count = engine.history.size
        session.reapply()
        assertEquals(count + 1, engine.history.size)
        assertEquals(session.current, engine.history.last())
    }

    @Test
    fun stateFlowMirrorsCurrent() {
        val session = EqualizerSession()
        session.selectPreset("dance")
        assertEquals(session.current, session.state.value)
        assertEquals("dance", session.state.value.presetId)
    }
}
