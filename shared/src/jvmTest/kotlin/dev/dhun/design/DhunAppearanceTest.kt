package dev.dhun.design

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import dev.dhun.data.SettingsKeys
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Candidate 28 gates. The load-bearing ones are the first two: they pin the
 * **dark** palette hex-for-hex, because "dark stays the default so nothing
 * regresses" is the whole promise of an additive theme system. Everything
 * after that is about the light scheme and accent selector being real.
 *
 * Note these run as plain JVM tests with no composer — which is exactly the
 * point. `DhunColors` has to answer correctly *outside* composition too
 * (`app-desktop`'s `TrayIcons` reads tokens at object-init).
 */
class DhunAppearanceTest {

    @BeforeTest
    fun resetBefore() {
        DhunAppearance.reset()
    }

    @AfterTest
    fun resetAfter() {
        DhunAppearance.reset()
    }

    @Test
    fun darkPlusBrandIsTheDefault() {
        assertEquals(DhunThemeMode.DARK, DhunAppearance.mode)
        assertEquals(DhunAccent.BRAND, DhunAppearance.accent)
        assertFalse(DhunAppearance.tokens.isLight)
        assertSame(DhunTokens.dark, DhunAppearance.tokens)
    }

    @Test
    fun darkPaletteIsByteIdenticalToTheShippedColors() {
        // Every value below is the pre-candidate-28 hex. If this test goes red,
        // a dark-mode screen changed appearance — which is out of scope.
        assertEquals(Color(0xFF0A0A0A), DhunColors.background, "background")
        assertEquals(Color(0xFF121212), DhunColors.surface, "surface")
        assertEquals(Color(0xFF1A1A1A), DhunColors.surfaceVariant, "surfaceVariant")
        assertEquals(Color(0xFF242424), DhunColors.surfaceElevated, "surfaceElevated")
        assertEquals(Color(0xFF2A2A2A), DhunColors.surfaceHighest, "surfaceHighest")
        assertEquals(Color(0xFF1E1E1E), DhunColors.surfaceCard, "surfaceCard")
        assertEquals(Color(0x5E20202A), DhunColors.glassHighlight, "glassHighlight")
        assertEquals(Color(0x7016161E), DhunColors.glass, "glass")
        assertEquals(Color(0x8A0E0E14), DhunColors.glassDeep, "glassDeep")
        assertEquals(Color(0xB8121218), DhunColors.glassStrong, "glassStrong")
        assertEquals(Color(0xC81C1C26), DhunColors.glassBarTop, "glassBarTop")
        assertEquals(Color(0x1FFFFFFF), DhunColors.glassSheen, "glassSheen")
        assertEquals(Color(0x38FFFFFF), DhunColors.glassEdge, "glassEdge")
        assertEquals(Color(0x80000000), DhunColors.scrim, "scrim")
        assertEquals(Color(0xCC000000), DhunColors.scrimStrong, "scrimStrong")
        assertEquals(Color(0x1AFFFFFF), DhunColors.border, "border")
        assertEquals(Color(0x33FFFFFF), DhunColors.borderStrong, "borderStrong")
        assertEquals(Color(0xFFFFFFFF), DhunColors.textPrimary, "textPrimary")
        assertEquals(Color(0xCCFFFFFF), DhunColors.textSecondary, "textSecondary")
        assertEquals(Color(0x99FFFFFF), DhunColors.textTertiary, "textTertiary")
        assertEquals(Color(0x61FFFFFF), DhunColors.textDisabled, "textDisabled")
        assertEquals(Color(0x4DFFFFFF), DhunColors.textHint, "textHint")
        assertEquals(Color(0xFFBB86FC), DhunColors.accent, "accent")
        assertEquals(Color(0xFF000000), DhunColors.onAccent, "onAccent")
        assertEquals(Color(0xFF3A2A5A), DhunColors.accentContainer, "accentContainer")
        assertEquals(Color(0xFFE8D5FF), DhunColors.onAccentContainer, "onAccentContainer")
        assertEquals(Color(0xFFCF6679), DhunColors.error, "error")
        assertEquals(Color(0xFF000000), DhunColors.onError, "onError")
        assertEquals(Color(0xFF4D1A24), DhunColors.errorContainer, "errorContainer")
        assertEquals(Color(0xFF4CAF50), DhunColors.success, "success")
        assertEquals(Color(0xFFFFB74D), DhunColors.warning, "warning")
        assertEquals(Color(0x40CF6679), DhunColors.borderError, "borderError")
        assertEquals(Color(0x0FFFFFFF), DhunColors.overlayHover, "overlayHover")
        assertEquals(Color(0x14FFFFFF), DhunColors.overlayPressed, "overlayPressed")
        assertEquals(Color(0x1FFFFFFF), DhunColors.overlayFocus, "overlayFocus")
        assertEquals(Color(0xFF1A1A1A), DhunColors.placeholderStart, "placeholderStart")
        assertEquals(Color(0xFF2A2A2A), DhunColors.placeholderEnd, "placeholderEnd")
        assertEquals(Color(0xFF333333), DhunColors.placeholderPulse, "placeholderPulse")
        assertEquals(Color(0xFF1E1E1E), DhunColors.shimmerBase, "shimmerBase")
        assertEquals(Color(0xFF2E2E2E), DhunColors.shimmerHighlight, "shimmerHighlight")
        // Tonal ladder — same values as before it was promoted to tokens.
        assertEquals(DhunColors.background, DhunColors.surfaceContainerLowest)
        assertEquals(DhunColors.surface, DhunColors.surfaceContainerLow)
        assertEquals(DhunColors.surfaceElevated, DhunColors.surfaceContainer)
        assertEquals(DhunColors.surfaceHighest, DhunColors.surfaceContainerHigh)
        assertEquals(Color(0xFF303030), DhunColors.surfaceContainerHighest)
    }

    @Test
    fun darkPlusBrandEqualsTheDarkBaselineByValue() {
        assertEquals(DhunTokens.dark, DhunTokens.forAppearance(DhunThemeMode.DARK, DhunAccent.BRAND))
    }

    @Test
    fun lightReachesEveryTokenReaderWithoutTouchingAScreen() {
        // The 274 static DhunColors reads in the shared/ui screens are why this
        // test exists: they have to follow the flip with no screen rewritten.
        DhunAppearance.setAppearance(DhunThemeMode.LIGHT, DhunAccent.BRAND)

        assertTrue(DhunAppearance.tokens.isLight)
        assertEquals(DhunTokens.light.background, DhunColors.background)
        assertEquals(DhunTokens.light.surface, DhunColors.surface)
        assertEquals(DhunTokens.light.textPrimary, DhunColors.textPrimary)
        assertEquals(DhunTokens.light.glass, DhunColors.glass)
        assertEquals(DhunTokens.light.border, DhunColors.border)
        assertEquals(DhunTokens.light.error, DhunColors.error)
        assertEquals(DhunTokens.light.shimmerBase, DhunColors.shimmerBase)
        // ...and nothing dark survives by omission.
        assertNotEquals(DhunTokens.dark.background, DhunColors.background)
        assertNotEquals(DhunTokens.dark.textPrimary, DhunColors.textPrimary)
        assertNotEquals(DhunTokens.dark.glass, DhunColors.glass)
    }

    @Test
    fun lightIsNotJustInvertedDark() {
        val light = DhunTokens.light
        val dark = DhunTokens.dark
        // Surfaces actually light, text actually dark.
        assertTrue(light.background.luma() > dark.background.luma(), "light background must be lighter")
        assertTrue(light.textPrimary.luma() < dark.textPrimary.luma(), "light text must be darker")
        // Glass flips from translucent black-with-white-edge to translucent
        // white-with-black-edge, or frosted chrome disappears on paper.
        assertTrue(light.glass.red > 0.9f && light.glass.blue > 0.9f, "light glass is a white veil")
        assertTrue(dark.glass.red < 0.2f, "dark glass is a black veil")
        assertTrue(light.border.red < 0.1f, "light border is a black hairline")
        assertTrue(dark.border.red > 0.9f, "dark border is a white hairline")
    }

    @Test
    fun accentSelectorSwapsOnlyTheFourAccentRoles() {
        DhunAppearance.setAppearance(DhunThemeMode.DARK, DhunAccent.JADE)

        val ramp = DhunAccent.JADE.dark
        assertEquals(ramp.accent, DhunColors.accent)
        assertEquals(ramp.onAccent, DhunColors.onAccent)
        assertEquals(ramp.container, DhunColors.accentContainer)
        assertEquals(ramp.onContainer, DhunColors.onAccentContainer)
        // Everything else is untouched — the accent is not a re-skin.
        assertEquals(DhunTokens.dark.background, DhunColors.background)
        assertEquals(DhunTokens.dark.textPrimary, DhunColors.textPrimary)
        assertEquals(DhunTokens.dark.error, DhunColors.error)
        assertEquals(DhunTokens.dark.glassStrong, DhunColors.glassStrong)
    }

    @Test
    fun everyAccentHasItsOwnDarkAndLightRamp() {
        DhunAccent.entries.forEach { accent ->
            assertNotEquals(
                accent.dark.accent,
                accent.light.accent,
                "${accent.name}: one hue cannot serve near-black and paper white",
            )
            // Each ramp must be internally consistent with its mode's surfaces.
            assertTrue(accent.dark.accent.luma() > 0.4f, "${accent.name} dark ramp must read on near-black")
            assertTrue(accent.light.accent.luma() < 0.4f, "${accent.name} light ramp must read on white")
        }
    }

    @Test
    fun brandAccentIsTheHistoricalVioletInDark() {
        assertEquals(Color(0xFFBB86FC), DhunAccent.BRAND.dark.accent)
        assertEquals(Color(0xFF3A2A5A), DhunAccent.BRAND.dark.container)
    }

    @Test
    fun setAppearanceIsANoOpWhenNothingChanged() {
        val before = DhunAppearance.tokens
        DhunAppearance.setAppearance(DhunThemeMode.DARK, DhunAccent.BRAND)
        // Same instance: no write, so no invalidation, so DhunTheme can call
        // this on every composition without churning state.
        assertSame(before, DhunAppearance.tokens)
    }

    @Test
    fun setAppearanceChangesTokensInstanceWhenSomethingChanged() {
        val before = DhunAppearance.tokens
        DhunAppearance.setAppearance(DhunThemeMode.LIGHT, DhunAccent.CYAN)
        assertFalse(before === DhunAppearance.tokens, "a real change must publish a new token set")
        DhunAppearance.reset()
        assertSame(DhunTokens.dark, DhunAppearance.tokens)
    }

    @Test
    fun materialSchemeIsDerivedFromTheActiveTokens() {
        listOf(
            DhunThemeMode.DARK to DhunAccent.BRAND,
            DhunThemeMode.DARK to DhunAccent.AMBER,
            DhunThemeMode.LIGHT to DhunAccent.ROSE,
        ).forEach { (mode, accent) ->
            DhunAppearance.setAppearance(mode, accent)
            val scheme = DhunColors.scheme
            assertEquals(DhunColors.accent, scheme.primary, "$mode/$accent primary")
            assertEquals(DhunColors.onAccent, scheme.onPrimary, "$mode/$accent onPrimary")
            assertEquals(DhunColors.accentContainer, scheme.primaryContainer, "$mode/$accent container")
            assertEquals(DhunColors.background, scheme.background, "$mode/$accent background")
            assertEquals(DhunColors.textPrimary, scheme.onBackground, "$mode/$accent onBackground")
            assertEquals(DhunColors.surface, scheme.surface, "$mode/$accent surface")
            assertEquals(DhunColors.surfaceVariant, scheme.surfaceVariant, "$mode/$accent surfaceVariant")
            assertEquals(DhunColors.textSecondary, scheme.onSurfaceVariant, "$mode/$accent onSurfaceVariant")
            assertEquals(DhunColors.error, scheme.error, "$mode/$accent error")
            assertEquals(DhunColors.errorContainer, scheme.errorContainer, "$mode/$accent errorContainer")
            assertEquals(DhunColors.borderStrong, scheme.outline, "$mode/$accent outline")
            assertEquals(DhunColors.border, scheme.outlineVariant, "$mode/$accent outlineVariant")
            assertEquals(DhunColors.scrim, scheme.scrim, "$mode/$accent scrim")
            assertEquals(DhunAppearance.tokens.tertiary, scheme.tertiary, "$mode/$accent tertiary")
        }
    }

    @Test
    fun schemeIsCachedPerTokenSet() {
        assertSame(DhunAppearance.tokens.materialScheme, DhunColors.scheme)
    }

    @Test
    fun unspecifiedSchemeRolesKeepTheirMaterial3Defaults() {
        // The scheme is built as `base.copy(...)`, so every role DHUN does not
        // set must still equal the stock M3 value — that is what keeps this
        // refactor from silently re-tuning, say, inverse surfaces.
        val dark = DhunTokens.dark.materialScheme
        val stockDark = darkColorScheme()
        assertEquals(stockDark.inverseSurface, dark.inverseSurface)
        assertEquals(stockDark.inverseOnSurface, dark.inverseOnSurface)
        assertEquals(stockDark.inversePrimary, dark.inversePrimary)
        assertEquals(stockDark.surfaceTint, dark.surfaceTint)

        val light = DhunTokens.light.materialScheme
        val stockLight = lightColorScheme()
        assertEquals(stockLight.inverseSurface, light.inverseSurface)
        assertEquals(stockLight.inverseOnSurface, light.inverseOnSurface)
        assertEquals(stockLight.inversePrimary, light.inversePrimary)
        assertEquals(stockLight.surfaceTint, light.surfaceTint)
    }

    @Test
    fun idsAreStableUniqueAndRoundTrip() {
        DhunThemeMode.entries.forEach { mode ->
            assertSame(mode, DhunThemeMode.fromId(mode.id))
        }
        assertEquals(
            DhunThemeMode.entries.size,
            DhunThemeMode.entries.map { it.id }.toSet().size,
            "DhunThemeMode ids must be unique",
        )
        DhunAccent.entries.forEach { accent ->
            assertSame(accent, DhunAccent.fromId(accent.id))
        }
        assertEquals(
            DhunAccent.entries.size,
            DhunAccent.entries.map { it.id }.toSet().size,
            "DhunAccent ids must be unique",
        )
        // Unknown or absent keys fall back rather than crashing — a corrupt
        // pref must never take the app down at startup.
        assertNull(DhunThemeMode.fromId("sepia"))
        assertNull(DhunThemeMode.fromId(null))
        assertNull(DhunAccent.fromId("chartreuse"))
        assertNull(DhunAccent.fromId(null))
    }

    @Test
    fun modeIdsMatchThePersistedVocabulary() {
        // SettingsKeys.THEME already documents "dark" | "light" | "system" and
        // defaults to "dark"; nothing consumes it yet, but when a host wires it
        // up these ids go straight into the DB. If that vocabulary moves, this
        // fails here rather than silently persisting an unreadable theme.
        assertEquals("dark", DhunThemeMode.DARK.id)
        assertEquals(SettingsKeys.THEME_DEFAULT, DhunThemeMode.default.id)
        DhunThemeMode.entries.forEach { mode ->
            assertTrue(
                mode.id in setOf("dark", "light", "system"),
                "${mode.name}.id='${mode.id}' is not a SettingsKeys.THEME value",
            )
        }
        // "system" is storable but not honoured yet (no expect/actual hook), so
        // it must map to null and fall back to dark instead of lying.
        assertNull(DhunThemeMode.fromId("system"))
    }

    @Test
    fun artworkFallbackFollowsTheSelectedAccent() {
        assertEquals(DhunColors.accent, ArtworkColors.fallback.primary)
        assertEquals(DhunColors.onAccent, ArtworkColors.fallback.onPrimary)

        DhunAppearance.setAppearance(DhunThemeMode.DARK, DhunAccent.JADE)
        assertEquals(DhunAccent.JADE.dark.accent, ArtworkColors.fallback.primary)

        DhunAppearance.setAppearance(DhunThemeMode.LIGHT, DhunAccent.ROSE)
        assertEquals(DhunAccent.ROSE.light.accent, ArtworkColors.fallback.primary)
        assertEquals(DhunAccent.ROSE.light.onAccent, ArtworkColors.fallback.onPrimary)
    }

    @Test
    fun artworkSeedsStillResolveAndStayInRange() {
        // The extractor path is untouched by the theme work; this just proves
        // the accent change did not break it in either mode.
        listOf(DhunThemeMode.DARK, DhunThemeMode.LIGHT).forEach { mode ->
            DhunAppearance.setAppearance(mode, DhunAccent.BRAND)
            listOf("vid-a", "vid-b", "", "  ").forEach { seed ->
                val colors = ArtworkColorExtractor.extractFromSeed(seed)
                assertEquals(1f, colors.primary.alpha)
                assertEquals(1f, colors.onPrimary.alpha)
            }
        }
    }

    /** Perceived (non-linearised) lightness — the same yardstick the design tokens use. */
    private fun Color.luma(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
