package dev.dhun.design.components

import kotlin.test.Test
import kotlin.test.assertTrue

class DhunSpinnerPolicyTest {
    @Test
    fun spinnerIsAnAccentArcWithAQuietTrack() {
        assertTrue(DhunSpinnerPolicy.ROTATION_MS > 0)
        assertTrue(DhunSpinnerPolicy.SWEEP_DEGREES > 0f)
        assertTrue(DhunSpinnerPolicy.SWEEP_DEGREES < 360f)
        assertTrue(DhunSpinnerPolicy.TRACK_ALPHA > 0f)
        assertTrue(DhunSpinnerPolicy.TRACK_ALPHA < 0.5f)
    }
}
