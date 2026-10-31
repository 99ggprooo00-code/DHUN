package dev.dhun.design.components

import dev.dhun.design.DhunAccent
import dev.dhun.design.DhunAppearance
import dev.dhun.design.DhunThemeMode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DhunControlPolicyTest {
    @BeforeTest fun resetBefore() = DhunAppearance.reset()
    @AfterTest fun resetAfter() = DhunAppearance.reset()

    @Test
    fun checkboxAndProgressUseTheActiveDhunTokens() {
        for (mode in DhunThemeMode.entries) {
            DhunAppearance.setAppearance(mode, DhunAccent.BRAND)
            assertEquals(DhunAppearance.tokens.glassStrong, DhunControlPolicy.checkboxFillOff())
            assertEquals(DhunAppearance.tokens.glassEdge, DhunControlPolicy.checkboxBorderOff())
            assertEquals(DhunAppearance.tokens.accent, DhunControlPolicy.checkboxFillOn())
            assertEquals(DhunAppearance.tokens.accent, DhunControlPolicy.checkboxBorderOn())
            assertEquals(DhunAppearance.tokens.onAccent, DhunControlPolicy.checkboxGlyphOn())
            assertEquals(DhunAppearance.tokens.accent, DhunControlPolicy.progressFill())
            assertEquals(DhunAppearance.tokens.glassStrong, DhunControlPolicy.progressTrack())
            assertTrue(DhunControlPolicy.checkboxFillOff().alpha < 1f)
            assertTrue(DhunControlPolicy.progressTrack().alpha < 1f)
        }
    }

    @Test
    fun disabledStateScalesAlphaWithoutChangingTheHue() {
        val color = DhunControlPolicy.checkboxFillOn()
        val disabled = DhunControlPolicy.disabled(color)
        assertEquals(color.alpha * DhunControlPolicy.DISABLED_ALPHA, disabled.alpha, 0.001f)
        assertEquals(color.red, disabled.red)
        assertEquals(color.green, disabled.green)
        assertEquals(color.blue, disabled.blue)
        assertEquals(color, DhunControlPolicy.disableIf(true, color))
        assertEquals(disabled, DhunControlPolicy.disableIf(false, color))
    }

    @Test
    fun progressIsNullableClampedAndProtectedFromNaN() {
        assertNull(DhunControlPolicy.progressValue(null))
        assertEquals(0f, DhunControlPolicy.progressValue(-0.5f))
        assertEquals(0f, DhunControlPolicy.progressValue(Float.NaN))
        assertEquals(0.4f, DhunControlPolicy.progressValue(0.4f))
        assertEquals(1f, DhunControlPolicy.progressValue(1.5f))
    }
}
