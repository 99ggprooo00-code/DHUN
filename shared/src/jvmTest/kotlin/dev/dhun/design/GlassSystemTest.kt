package dev.dhun.design

import androidx.compose.ui.graphics.Color
import dev.dhun.design.components.GlassMaterialPolicy
import dev.dhun.design.components.DhunControlPolicy
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The glass material, as numbers.
 *
 * Why this file exists: the app's visual identity is frosted glass, and the
 * recurring regression is a surface that *compiles*, passes every functional
 * test and still looks like stock Material 3 because it is one flat fill. That
 * is not detectable from a screenshot in CI, so the recipe is pinned here:
 *
 *  - the body is a **gradient** (top lifted, bottom deepened), not one colour;
 *  - the sheen fades to nothing and is capped below a wash-out;
 *  - the edge is a **gradient** (bright top, faint bottom), not a uniform stroke;
 *  - dialogs stay above the row fill's opacity floor, so "opaque enough" never
 *    becomes "flat" and contrast gates keep their meaning;
 *  - the switch/slider palette is DHUN's, not Material's defaults.
 *
 * These assert the *policy*, not a rendered frame: no emulator or device is
 * available in this environment (`docs/design/glass-system.md` §6).
 */
class GlassSystemTest {

    @BeforeTest
    fun resetBefore() = DhunAppearance.reset()

    @AfterTest
    fun resetAfter() = DhunAppearance.reset()

    private fun luminance(color: Color): Float =
        0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

    @Test
    fun theBodyIsAGradientNotOneFlatColour() {
        for (light in listOf(false, true)) {
            val base = if (light) Color(0xFFEFE9F2) else Color(0xFF2A2020)
            val stops = GlassMaterialPolicy.bodyStops(base, light)
            assertEquals(3, stops.size, "a glass body has a lifted top, the base and a deep bottom")
            assertEquals(listOf(0f, GlassMaterialPolicy.BODY_MID_STOP, 1f), stops.map { it.first })
            // Offsets are ascending, so the brush is well-formed.
            stops.zipWithNext().forEach { (a, b) -> assertTrue(b.first > a.first) }

            val top = stops.first().second
            val mid = stops[1].second
            val bottom = stops.last().second
            assertTrue(
                luminance(top) > luminance(mid),
                "light=$light: the top must be lifted, otherwise the panel reads flat",
            )
            assertTrue(
                luminance(bottom) < luminance(mid),
                "light=$light: the bottom must deepen, otherwise the panel reads flat",
            )
            // The whole gradient stays within a narrow band of the readable fill:
            // this is depth, not a wash.
            (stops.map { it.second }).forEach {
                assertTrue(
                    abs(luminance(it) - luminance(mid)) < 0.12f,
                    "light=$light: body stop drifted too far from the readable fill",
                )
            }
            // Alpha is the caller's readability decision and must not be changed.
            stops.forEach { assertEquals(base.alpha, it.second.alpha) }
        }
    }

    @Test
    fun theSheenIsVisibleCappedAndFadesOut() {
        for (light in listOf(false, true)) {
            val stops = GlassMaterialPolicy.sheenStops(light)
            assertEquals(0f, stops.first().first)
            assertTrue(stops.first().second > 0f, "light=$light: a glass top needs a specular sheen")
            assertTrue(
                stops.first().second <= GlassMaterialPolicy.SHEEN_TOP,
                "light=$light: the sheen may not exceed its cap",
            )
            assertEquals(0f, stops.last().second, "light=$light: the sheen must fade to nothing")
            assertEquals(GlassMaterialPolicy.SHEEN_EXTENT, stops.last().first)
            stops.zipWithNext().forEach { (a, b) ->
                assertTrue(b.first > a.first, "light=$light: sheen stops must ascend")
                assertTrue(b.second <= a.second, "light=$light: the sheen may never brighten downwards")
            }
            // Light glass takes a weaker sheen — white on white washes out.
            if (light) {
                assertTrue(
                    stops.first().second < GlassMaterialPolicy.sheenStops(false).first().second,
                    "the light-theme sheen must be weaker than the dark one",
                )
            }
        }
    }

    @Test
    fun theEdgeIsAGradientHairlineNotAUniformOutline() {
        for (light in listOf(false, true)) {
            val stops = GlassMaterialPolicy.edgeStops(light)
            assertEquals(0f, stops.first().first)
            assertEquals(1f, stops.last().first)
            assertTrue(
                stops.first().second > stops.last().second,
                "light=$light: the edge must catch light at the top and fade at the bottom",
            )
            assertTrue(
                stops.last().second > 0f,
                "light=$light: the panel still needs an edge at the bottom, just a faint one",
            )
        }
    }

    @Test
    fun aDialogBodyStaysAboveTheRowsOpacityFloorButBelowOpaque() {
        for (light in listOf(false, true)) {
            val body = GlassMaterialPolicy.dialogBody(light, Color(0xFF202028))
            val floor = if (light) {
                GlassMaterialPolicy.DIALOG_BODY_ALPHA_LIGHT
            } else {
                GlassMaterialPolicy.DIALOG_BODY_ALPHA_DARK
            }
            assertEquals(floor, body.alpha, "the dialog floor is a documented number, not a per-call choice")
            assertTrue(body.alpha < 1f, "a dialog is still glass: it may not become an opaque plate")
            assertTrue(body.alpha > 0.85f, "a dialog must stay readable over artwork")
        }
    }

    @Test
    fun theBlendHelperMovesTowardsTheTargetAndKeepsAlpha() {
        val base = Color(0.2f, 0.3f, 0.4f, 0.5f)
        val lifted = GlassMaterialPolicy.blend(base, Color.White, 0.5f)
        assertEquals(0.6f, lifted.red, 0.001f)
        assertEquals(0.65f, lifted.green, 0.001f)
        assertEquals(0.7f, lifted.blue, 0.001f)
        assertEquals(0.5f, lifted.alpha, "alpha is the caller's decision, never the blend's")
        // Out-of-range fractions are clamped, so a bad constant cannot invert a surface.
        assertEquals(base, GlassMaterialPolicy.blend(base, Color.White, 0f))
        val full = GlassMaterialPolicy.blend(base, Color.Black, 2f)
        assertEquals(0f, full.red, 0.001f)
        assertEquals(0.5f, full.alpha)
    }

    @Test
    fun theControlsUseDhunsPaletteNotMaterialsDefaults() {
        // Off-state is glass, not Material's grey groove; on-state is the accent.
        assertEquals(DhunColors.glassStrong, DhunControlPolicy.switchTrackOff())
        assertEquals(DhunColors.textSecondary, DhunControlPolicy.switchThumbOff())
        assertEquals(DhunColors.glassEdge, DhunControlPolicy.switchBorderOff())
        assertEquals(DhunColors.accent, DhunControlPolicy.switchTrackOn())
        assertEquals(DhunColors.onAccent, DhunControlPolicy.switchThumbOn())
        assertNotEquals(
            DhunControlPolicy.switchThumbOn(),
            DhunControlPolicy.switchTrackOn(),
            "the knob must stay distinguishable on the accent track",
        )

        assertEquals(DhunColors.accent, DhunControlPolicy.sliderActiveTrack())
        assertEquals(DhunColors.accent, DhunControlPolicy.sliderThumb())
        // The unfilled groove is translucent glass, so the artwork reads through.
        assertEquals(DhunColors.glassStrong, DhunControlPolicy.sliderInactiveTrack())
        assertTrue(
            DhunControlPolicy.sliderInactiveTrack().alpha < 1f,
            "an opaque slider groove is a Material default, not DHUN glass",
        )
        assertEquals(DhunControlPolicy.DISABLED_ALPHA, DhunControlPolicy.disabled(DhunColors.accent).alpha, 0.001f)
        assertEquals(0.38f, DhunControlPolicy.DISABLED_ALPHA, 0.001f, "the disabled opacity is a documented number")
    }

    @Test
    fun theCheckboxIsGlassWhenUncheckedAndTheAccentWhenChecked() {
        assertEquals(DhunColors.glassStrong, DhunControlPolicy.checkboxFillOff())
        assertEquals(DhunColors.glassEdge, DhunControlPolicy.checkboxBorderOff())
        // The unchecked box is a translucent glass square, not an opaque outline.
        assertTrue(
            DhunControlPolicy.checkboxFillOff().alpha < 1f,
            "an opaque unchecked box is a Material checkbox, not DHUN glass",
        )
        assertEquals(DhunColors.accent, DhunControlPolicy.checkboxFillOn())
        assertEquals(DhunColors.onAccent, DhunControlPolicy.checkboxGlyphOn())
        assertNotEquals(
            DhunControlPolicy.checkboxGlyphOn(),
            DhunControlPolicy.checkboxFillOn(),
            "the tick must stay visible on the accent fill",
        )
        // enableIf only touches opacity, never the hue.
        val disabledFill = DhunControlPolicy.disableIf(false, DhunControlPolicy.checkboxFillOn())
        assertEquals(DhunColors.accent, disabledFill.copy(alpha = DhunColors.accent.alpha))
        assertEquals(DhunControlPolicy.DISABLED_ALPHA, disabledFill.alpha, 0.001f)
        assertEquals(
            DhunColors.accent,
            DhunControlPolicy.disableIf(true, DhunColors.accent),
            "an enabled control keeps full opacity",
        )
    }
}
