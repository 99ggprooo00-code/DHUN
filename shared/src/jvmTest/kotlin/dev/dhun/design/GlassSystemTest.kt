package dev.dhun.design

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import dev.dhun.design.components.AmbientBackdropPolicy
import dev.dhun.design.components.DhunControlPolicy
import dev.dhun.design.components.DhunNavigationPolicy
import dev.dhun.design.components.DhunSpinnerPolicy
import dev.dhun.design.components.GlassMaterialPolicy
import dev.dhun.ui.shell.DhunShellPolicy
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

    companion object {
        /**
         * Compose stores colours at 8 bits per channel, so 0.91 round-trips as
         * 0.9098 and a 0.5 blend of 0.4 lands on 0.702. Assertions on channels
         * have to allow that quantisation; the *policy* numbers are exact.
         */
        const val CHANNEL_TOLERANCE = 0.01f
    }

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
            stops.map { it.second }.forEach {
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
            // Compose packs alpha to 8 bits, so 0.91 comes back as 0.9098…
            assertEquals(floor, body.alpha, CHANNEL_TOLERANCE,
                "the dialog floor is a documented number, not a per-call choice")
            assertTrue(body.alpha < 1f, "a dialog is still glass: it may not become an opaque plate")
            assertTrue(body.alpha > 0.85f, "a dialog must stay readable over artwork")
        }
    }

    @Test
    fun theBlendHelperMovesTowardsTheTargetAndKeepsAlpha() {
        val base = Color(0.2f, 0.3f, 0.4f, 0.5f)
        val lifted = GlassMaterialPolicy.blend(base, Color.White, 0.5f)
        assertEquals(0.6f, lifted.red, CHANNEL_TOLERANCE)
        assertEquals(0.65f, lifted.green, CHANNEL_TOLERANCE)
        assertEquals(0.7f, lifted.blue, CHANNEL_TOLERANCE)
        assertEquals(0.5f, lifted.alpha, CHANNEL_TOLERANCE, "alpha is the caller's decision, never the blend's")
        // A zero fraction is a no-op, so a bad constant cannot shift a surface.
        val unchanged = GlassMaterialPolicy.blend(base, Color.White, 0f)
        assertEquals(base.red, unchanged.red, CHANNEL_TOLERANCE)
        assertEquals(base.green, unchanged.green, CHANNEL_TOLERANCE)
        assertEquals(base.blue, unchanged.blue, CHANNEL_TOLERANCE)
        assertEquals(base.alpha, unchanged.alpha, CHANNEL_TOLERANCE)
        // Out-of-range fractions are clamped, so a bad constant cannot invert a surface.
        val full = GlassMaterialPolicy.blend(base, Color.Black, 2f)
        assertEquals(0f, full.red, CHANNEL_TOLERANCE)
        assertEquals(0.5f, full.alpha, CHANNEL_TOLERANCE)
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

    @Test
    fun theSpinnerIsAnArcInATrackNotASolidRing() {
        // Less than a full turn, or it reads as a static circle.
        assertTrue(
            DhunSpinnerPolicy.SWEEP_DEGREES in 180f..340f,
            "the visible arc must be shorter than the circle: ${DhunSpinnerPolicy.SWEEP_DEGREES}",
        )
        // The track must be a hint, never a second ring at full strength.
        assertTrue(
            DhunSpinnerPolicy.TRACK_ALPHA > 0f && DhunSpinnerPolicy.TRACK_ALPHA < 0.35f,
            "the glass track is a hint, was ${DhunSpinnerPolicy.TRACK_ALPHA}",
        )
        // A full rotation per cycle, and slow enough not to strobe.
        assertTrue(DhunSpinnerPolicy.ROTATION_MS in 400..2000)
    }

    @Test
    fun theAmbientBackdropStaysWithinTheContrastBand() {
        val dark = AmbientBackdropPolicy.bloomAlpha(light = false)
        val light = AmbientBackdropPolicy.bloomAlpha(light = true)
        // Subtle: the blooms must never become a colour wash that the row
        // contrast gates were not written for.
        assertTrue(dark > 0f && dark <= 0.20f, "dark bloom alpha $dark")
        assertTrue(light > 0f && light <= 0.20f, "light bloom alpha $light")
        assertTrue(light < dark, "white-on-white needs a weaker bloom than the dark theme")
        assertTrue(AmbientBackdropPolicy.SECONDARY_SCALE in 0f..1f)
        assertTrue(AmbientBackdropPolicy.BLOOM_RADIUS > 0f)

        val size = Size(1200f, 800f)
        val blooms = AmbientBackdropPolicy.blooms(
            size = size,
            accent = Color(0xFF7A5CC4),
            secondary = Color(0xFF2E6F6A),
            bloomAlpha = dark,
        )
        assertEquals(2, blooms.size, "an accent bloom and one cool bloom, nothing more")
        blooms.forEach { bloom ->
            assertTrue(bloom.radius > 0f)
            // Radii scale with the viewport, so a desktop window does not get a
            // phone-sized dot.
            assertTrue(bloom.radius <= size.width && bloom.radius <= size.height)
            assertTrue(bloom.center.x in 0f..size.width, "bloom centre off-canvas: ${bloom.center}")
            assertTrue(bloom.center.y in 0f..size.height, "bloom centre off-canvas: ${bloom.center}")
            assertTrue(bloom.color.alpha > 0f && bloom.color.alpha <= 0.20f)
        }
        assertTrue(
            blooms.first().color.alpha > blooms.last().color.alpha,
            "the accent bloom leads; the cool one is a faint companion",
        )
    }

    @Test
    fun ambientCustomColoursRespondToBrightnessWithoutChangingTheDefaultPalette() {
        val neutral = DhunAppearance.BACKDROP_BRIGHTNESS_DEFAULT
        assertEquals(Color.Transparent, AmbientBackdropPolicy.brightnessOverlay(neutral))

        val dark = AmbientBackdropPolicy.brightnessOverlay(0)
        val bright = AmbientBackdropPolicy.brightnessOverlay(100)
        assertEquals(Color.Black.copy(alpha = dark.alpha), dark)
        assertEquals(Color.White.copy(alpha = bright.alpha), bright)
        assertTrue(dark.alpha > 0f && dark.alpha <= 0.28f)
        assertTrue(bright.alpha > 0f && bright.alpha <= 0.22f)
        assertEquals(dark, AmbientBackdropPolicy.brightnessOverlay(-100))
        assertEquals(bright, AmbientBackdropPolicy.brightnessOverlay(200))

        // Composite over a customized solid colour: lowering brightness must
        // reduce its channels and raising it must increase them, rather than
        // leaving the colour-only path untouched as it was before.
        val custom = Color(0xFF66507A)
        fun composite(overlay: Color): Color = Color(
            red = custom.red * (1f - overlay.alpha) + overlay.red * overlay.alpha,
            green = custom.green * (1f - overlay.alpha) + overlay.green * overlay.alpha,
            blue = custom.blue * (1f - overlay.alpha) + overlay.blue * overlay.alpha,
        )
        val lowered = composite(dark)
        val raised = composite(bright)
        assertTrue(lowered.red < custom.red && lowered.green < custom.green && lowered.blue < custom.blue)
        assertTrue(raised.red > custom.red && raised.green > custom.green && raised.blue > custom.blue)
    }

    @Test
    fun theNavigationChromeNeverFallsBackToMaterialsPill() {
        // The selected tab is a translucent accent pill, never a solid block:
        // the filled accent belongs to buttons, not to chrome.
        for (light in listOf(true, false)) {
            val top = DhunNavigationPolicy.pillTopAlpha(light)
            val bottom = DhunNavigationPolicy.pillBottomAlpha(light)
            assertTrue(top > 0f && top < 0.5f, "pill top alpha $top (light=$light)")
            assertTrue(bottom > 0f && bottom < top, "the pill must fade downward: $bottom vs $top")
            // The rim is the strongest part of the pill, or the edge disappears
            // over a pale cover.
            assertTrue(
                DhunNavigationPolicy.rimAlpha(light) > bottom,
                "rim must out-read the fill: ${DhunNavigationPolicy.rimAlpha(light)}",
            )
            assertTrue(DhunNavigationPolicy.rimAlpha(light) <= 0.6f)
        }
        // White-on-white needs a weaker pill than the dark theme; the same
        // numbers in both themes is how chrome washes out in light mode.
        assertTrue(
            DhunNavigationPolicy.pillTopAlpha(light = true) <
                DhunNavigationPolicy.pillTopAlpha(light = false),
        )

        val accent = Color(0xFF7A5CC4)
        // At progress 0 nothing is painted, so an unselected tab has no outline.
        assertEquals(
            0f,
            DhunNavigationPolicy.rimColor(accent, light = false, progress = 0f).alpha,
        )
        // …and it fades monotonically as the tab is left.
        assertEquals(
            DhunNavigationPolicy.rimAlpha(light = false) * 0.5f,
            DhunNavigationPolicy.rimColor(accent, light = false, progress = 0.5f).alpha,
            0.01f,
        )
        // The glyph follows the same curve: tertiary until the pill arrives.
        assertEquals(
            DhunNavigationPolicy.tint(selected = false, progress = 1f),
            DhunNavigationPolicy.tint(selected = false, progress = 0f),
        )

        // The pill has to fit the glyph plus a hair of padding, and the rail has
        // to fit the pill.
        assertTrue(DhunNavigationPolicy.pillWidth > DhunNavigationPolicy.iconSize)
        assertTrue(DhunNavigationPolicy.pillHeight > DhunNavigationPolicy.iconSize)
        assertTrue(DhunNavigationPolicy.railWidth > DhunNavigationPolicy.pillWidth)
    }

    @Test
    fun theRailIsExactlyTheWidthThePaneSplitReserves() {
        // Two places decide the rail's width: the shell's layout arithmetic and
        // the rail itself. If they disagree by even a few dp, the master pane
        // overflows at the two-pane breakpoint.
        assertEquals(
            DhunShellPolicy.railAllowance(hasRail = true),
            DhunNavigationPolicy.railWidth,
        )
    }
}
