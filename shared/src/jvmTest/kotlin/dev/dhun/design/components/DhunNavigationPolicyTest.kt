package dev.dhun.design.components

import androidx.compose.ui.graphics.Color
import dev.dhun.design.DhunAppearance
import dev.dhun.ui.shell.DhunShellPolicy
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DhunNavigationPolicyTest {
    @BeforeTest fun resetBefore() = DhunAppearance.reset()
    @AfterTest fun resetAfter() = DhunAppearance.reset()

    @Test
    fun selectionPillIsTranslucentAndLightModeIsIndependentlyTuned() {
        for (light in listOf(false, true)) {
            val top = DhunNavigationPolicy.pillTopAlpha(light)
            val bottom = DhunNavigationPolicy.pillBottomAlpha(light)
            assertTrue(top > 0f && top < 0.5f)
            assertTrue(bottom > 0f && bottom < top)
            assertTrue(DhunNavigationPolicy.rimAlpha(light) > bottom)
            assertTrue(DhunNavigationPolicy.rimAlpha(light) <= 0.6f)
        }
        assertTrue(
            DhunNavigationPolicy.pillTopAlpha(light = true) <
                DhunNavigationPolicy.pillTopAlpha(light = false),
        )

        val accent = Color(0xFF7A5CC4)
        assertEquals(0f, DhunNavigationPolicy.rimColor(accent, light = false, progress = 0f).alpha)
        assertEquals(
            DhunNavigationPolicy.rimAlpha(light = false) * 0.5f,
            DhunNavigationPolicy.rimColor(accent, light = false, progress = 0.5f).alpha,
            0.01f,
        )
        assertEquals(
            DhunNavigationPolicy.tint(selected = false, progress = 1f),
            DhunNavigationPolicy.tint(selected = false, progress = 0f),
        )
    }

    @Test
    fun navigationGeometryFitsIconsAndMatchesTheShellRailAllowance() {
        assertTrue(DhunNavigationPolicy.pillWidth > DhunNavigationPolicy.iconSize)
        assertTrue(DhunNavigationPolicy.pillHeight > DhunNavigationPolicy.iconSize)
        assertTrue(DhunNavigationPolicy.railWidth > DhunNavigationPolicy.pillWidth)
        assertEquals(DhunShellPolicy.railAllowance(hasRail = true), DhunNavigationPolicy.railWidth)
    }
}
