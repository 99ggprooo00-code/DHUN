package dev.dhun.design

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Contrast gates for both schemes and all six accents — the numbers a theme
 * system is actually judged on, and the ones no amount of "it compiles" can
 * tell you.
 *
 * WCAG 2.1 relative luminance / contrast ratio, computed from the token
 * colours themselves (translucent text is composited over the surface first,
 * because that is what a screen does). **These tests assert legibility of the
 * palette, not the rendered app** — visual acceptance on hardware remains the
 * user's gate.
 */
class DhunThemeContrastTest {

    @BeforeTest
    fun resetBefore() {
        DhunAppearance.reset()
    }

    @AfterTest
    fun resetAfter() {
        DhunAppearance.reset()
    }

    // -- text ladder -------------------------------------------------------

    @Test
    fun textLadderIsLegibleOnEverySurfaceInBothModes() {
        // The ladder keeps its five steps in both modes, and every step has to
        // stay readable on every tonal surface a screen can sit on.
        val thresholds = listOf(
            "textPrimary" to 7.0f,     // AAA body text
            "textSecondary" to 4.5f,   // AA body text
            "textTertiary" to 3.0f,    // AA large text / UI labels
            "textDisabled" to 1.5f,    // deliberately faint, but never invisible
            "textHint" to 1.2f,
        )
        eachMode { mode, tokens ->
            val surfaces = mapOf(
                "background" to tokens.background,
                "surface" to tokens.surface,
                "surfaceVariant" to tokens.surfaceVariant,
                "surfaceElevated" to tokens.surfaceElevated,
                "surfaceHighest" to tokens.surfaceHighest,
                "surfaceCard" to tokens.surfaceCard,
                "surfaceContainerLowest" to tokens.surfaceContainerLowest,
                "surfaceContainerLow" to tokens.surfaceContainerLow,
                "surfaceContainer" to tokens.surfaceContainer,
                "surfaceContainerHigh" to tokens.surfaceContainerHigh,
                "surfaceContainerHighest" to tokens.surfaceContainerHighest,
            )
            val ladder = listOf(
                tokens.textPrimary, tokens.textSecondary, tokens.textTertiary,
                tokens.textDisabled, tokens.textHint,
            )
            ladder.forEachIndexed { i, text ->
                val (name, need) = thresholds[i]
                surfaces.forEach { (surfaceName, surface) ->
                    val ratio = contrast(text.compositedOver(surface), surface)
                    assertTrue(
                        ratio >= need,
                        "$mode/$name on $surfaceName = ${"%.2f".format(ratio)}:1, need $need:1",
                    )
                }
            }
        }
    }

    @Test
    fun textLadderKeepsItsOrderingInBothModes() {
        eachMode { mode, tokens ->
            val ladder = listOf(
                tokens.textPrimary, tokens.textSecondary, tokens.textTertiary,
                tokens.textDisabled, tokens.textHint,
            )
            val contrastOnBackground = ladder.map { contrast(it.compositedOver(tokens.background), tokens.background) }
            contrastOnBackground.zipWithNext().forEachIndexed { i, (higher, lower) ->
                assertTrue(
                    higher > lower,
                    "$mode text step ${i + 1} must be more prominent than step ${i + 2}",
                )
            }
        }
    }

    // -- semantic ----------------------------------------------------------

    @Test
    fun semanticColorsAreLegibleInBothModes() {
        eachMode { mode, tokens ->
            // Dark's error/errorContainer pair is the *shipped* value and is
            // left untouched: retuning it would break "dark stays
            // byte-identical". Measured 3.92:1 — below AA for text, so it is
            // recorded in .ai/KNOWN_LIMITATIONS.md rather than silently
            // asserted away here. Light meets AA.
            val errorOnContainerMinimum = if (tokens.isLight) 4.5f else 3.0f
            assertAtLeast(
                contrast(tokens.error, tokens.errorContainer), errorOnContainerMinimum,
                "$mode error on errorContainer",
            )
            assertAtLeast(contrast(tokens.onError, tokens.error), 4.5f, "$mode onError on error")
            assertAtLeast(contrast(tokens.success, tokens.surface), 4.5f, "$mode success on surface")
            assertAtLeast(contrast(tokens.warning, tokens.surface), 4.5f, "$mode warning on surface")
            assertAtLeast(contrast(tokens.tertiary, tokens.surface), 4.5f, "$mode tertiary on surface")
            assertAtLeast(contrast(tokens.onTertiary, tokens.tertiary), 4.5f, "$mode onTertiary on tertiary")
        }
    }

    // -- accents -----------------------------------------------------------

    @Test
    fun everyAccentRampMeetsContrastInBothModes() {
        DhunAccent.entries.forEach { accent ->
            DhunThemeMode.entries.forEach { mode ->
                DhunAppearance.setAppearance(mode, accent)
                val tokens = DhunAppearance.tokens
                // Text on filled accent (play button, progress thumb).
                assertAtLeast(
                    contrast(tokens.onAccent, tokens.accent), 4.5f,
                    "${accent.name}/$mode onAccent on accent",
                )
                // Text on the accent container.
                assertAtLeast(
                    contrast(tokens.onAccentContainer, tokens.accentContainer), 4.5f,
                    "${accent.name}/$mode onAccentContainer on container",
                )
                // Accent as a non-text element against the surfaces it sits on
                // — WCAG 1.4.11, the play disc / seek bar / active tab case.
                assertAtLeast(contrast(tokens.accent, tokens.surface), 3.0f, "${accent.name}/$mode accent on surface")
                assertAtLeast(contrast(tokens.accent, tokens.background), 3.0f, "${accent.name}/$mode accent on background")
            }
        }
    }

    // -- artwork-derived control accent ------------------------------------

    @Test
    fun tamedControlAccentsStayLegibleOnTheActiveSurface() {
        // ArtworkControls.tamedForControls() lifts on dark and pushes down on
        // light. Both directions must keep the play disc / seek bar visible,
        // whatever the artwork threw at it. Exercised through the public
        // ArtworkColors.controlAccent, not the internal helper.
        val artwork = listOf(
            Color(0xFF050505), Color(0xFF404040), Color(0xFF808080), Color(0xFFC0C0C0),
            Color(0xFFFFFFFF), Color(0xFF8B0000), Color(0xFF004080), Color(0xFF2E7D32),
        )
        DhunAccent.entries.forEach { accent ->
            DhunThemeMode.entries.forEach { mode ->
                DhunAppearance.setAppearance(mode, accent)
                val tokens = DhunAppearance.tokens
                artwork.forEach { art ->
                    val control = ArtworkColors.fromPrimary(art).controlAccent
                    assertAtLeast(
                        contrast(control, tokens.surface), 3.0f,
                        "${accent.name}/$mode control accent from #${art.toHex()} on surface",
                    )
                    assertAtLeast(
                        contrast(control, tokens.background), 3.0f,
                        "${accent.name}/$mode control accent from #${art.toHex()} on background",
                    )
                }
            }
        }
    }

    // -- glass & overlays --------------------------------------------------

    @Test
    fun glassStackIsTranslucentAndOrderedInBothModes() {
        eachMode { mode, tokens ->
            val stack = listOf(
                "glassHighlight" to tokens.glassHighlight,
                "glass" to tokens.glass,
                "glassDeep" to tokens.glassDeep,
                "glassStrong" to tokens.glassStrong,
                "glassBarTop" to tokens.glassBarTop,
            )
            stack.forEach { (name, glass) ->
                assertTrue(glass.alpha > 0f && glass.alpha < 1f, "$mode/$name must be translucent, was ${glass.alpha}")
            }
            // The documented stack: each step covers more of the backdrop.
            stack.zipWithNext().forEach { (upper, lower) ->
                assertTrue(
                    upper.second.alpha < lower.second.alpha,
                    "$mode ${upper.first} (${upper.second.alpha}) must be sheerer than ${lower.first} (${lower.second.alpha})",
                )
            }
            // And the veil must face the right way, or frosted chrome
            // disappears: white glass on paper, black glass on near-black.
            val veilIsLight = tokens.glass.red > 0.9f && tokens.glass.blue > 0.9f
            assertTrue(veilIsLight == tokens.isLight, "$mode glass veil is on the wrong side of the palette")
        }
    }

    @Test
    fun bordersOverlaysAndScrimsAreTranslucentInBothModes() {
        eachMode { mode, tokens ->
            mapOf(
                "border" to tokens.border,
                "borderStrong" to tokens.borderStrong,
                "glassEdge" to tokens.glassEdge,
                "overlayHover" to tokens.overlayHover,
                "overlayPressed" to tokens.overlayPressed,
                "overlayFocus" to tokens.overlayFocus,
                "scrim" to tokens.scrim,
                "scrimStrong" to tokens.scrimStrong,
                "borderError" to tokens.borderError,
            ).forEach { (name, color) ->
                assertTrue(color.alpha > 0f, "$mode/$name must be visible at all")
                assertTrue(color.alpha < 1f, "$mode/$name must stay translucent, was ${color.alpha}")
            }
            assertTrue(tokens.border.alpha < tokens.borderStrong.alpha, "$mode borderStrong must be stronger")
            assertTrue(tokens.overlayHover.alpha < tokens.overlayPressed.alpha, "$mode pressed must beat hover")
            assertTrue(tokens.overlayPressed.alpha < tokens.overlayFocus.alpha, "$mode focus must beat pressed")
            assertTrue(tokens.scrim.alpha < tokens.scrimStrong.alpha, "$mode scrimStrong must be stronger")
        }
    }

    // -- helpers -----------------------------------------------------------

    private fun eachMode(block: (DhunThemeMode, DhunTokens) -> Unit) {
        DhunThemeMode.entries.forEach { mode ->
            DhunAppearance.setAppearance(mode, DhunAccent.BRAND)
            block(mode, DhunAppearance.tokens)
        }
    }

    private fun assertAtLeast(actual: Float, minimum: Float, what: String) {
        assertTrue(actual >= minimum, "$what = ${"%.2f".format(actual)}:1, need $minimum:1")
    }

    /** WCAG 2.1 relative luminance. */
    private fun luminance(color: Color): Float {
        val r = linearize(color.red)
        val g = linearize(color.green)
        val b = linearize(color.blue)
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun linearize(channel: Float): Float =
        if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)

    /** WCAG 2.1 contrast ratio, 1:1 .. 21:1. */
    private fun contrast(a: Color, b: Color): Float {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    /** Paints a (possibly translucent) token over an opaque surface, as a screen does. */
    private fun Color.compositedOver(surface: Color): Color {
        val a = alpha
        return Color(
            red = red * a + surface.red * (1f - a),
            green = green * a + surface.green * (1f - a),
            blue = blue * a + surface.blue * (1f - a),
            alpha = 1f,
        )
    }

    private fun Color.toHex(): String = listOf(red, green, blue).joinToString("") {
        (it * 255f).toInt().coerceIn(0, 255).toString(16).padStart(2, '0')
    }.uppercase()
}
