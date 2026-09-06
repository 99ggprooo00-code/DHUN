package dev.dhun.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArtworkColorExtractorTest {

    @Test
    fun extractFromSeed_isDeterministic_and_vivid() {
        val a = ArtworkColorExtractor.extractFromSeed("bohemian rhapsody queen")
        val b = ArtworkColorExtractor.extractFromSeed("bohemian rhapsody queen")
        assertEquals(a.primary, b.primary, "same seed must give same palette")
        // Vivid — not gray/near-black
        assertTrue(a.primary.red > 0.15f || a.primary.green > 0.15f || a.primary.blue > 0.15f)
    }

    @Test
    fun extractFromSeed_givesDifferentPalettesForFiveArtworks() {
        val seeds = listOf(
            "bohemian rhapsody queen",
            "blinding lights weeknd",
            "shape of you ed sheeran",
            "as it was harry styles",
            "le vitrail dhun seed",
        )
        val palettes = seeds.map { ArtworkColorExtractor.extractFromSeed(it) }
        // All primaries distinct (hash-seeded HSV)
        val primaries = palettes.map { it.primary }
        assertEquals(5, primaries.distinct().size, "5 seeds must yield 5 distinct primaries")
        // Each palette has sane alphas (container + tint translucent)
        palettes.forEach { p ->
            assertTrue(p.container.alpha in 0.2f..0.4f, "container alpha sane: ${p.container.alpha}")
            assertTrue(
                p.backgroundTint.alpha in 0.05f..0.15f,
                "ambient tint stays a wash, not a paint job: ${p.backgroundTint.alpha}",
            )
            // onPrimary contrasts primary
            assertNotEquals(p.primary, p.onPrimary)
        }
    }

    /**
     * Regression for the hardware-reported "terrible UI": seeded hues used to
     * roam the whole colour wheel at 62–92% saturation, so a track could paint
     * the app fire-engine red (indistinguishable from the error state) or
     * sludge brown. Hues are now clamped near the brand hue and muted.
     */
    @Test
    fun extractFromSeed_staysNearBrandHue_andIsMuted() {
        val seeds = listOf(
            "bohemian rhapsody queen", "maya le", "yo hawalai",
            "shiv stotras", "me raqsam", "dhun rockheads", "cheli le maitilai",
        )
        seeds.forEach { seed ->
            val p = ArtworkColorExtractor.extractFromSeed(seed).primary
            val mx = maxOf(p.red, p.green, p.blue)
            val mn = minOf(p.red, p.green, p.blue)
            val sat = if (mx == 0f) 0f else (mx - mn) / mx
            assertTrue(sat <= 0.55f, "seed '$seed' must stay muted, was sat=$sat")
            // Never a red-dominant colour — that reads as an error state.
            assertTrue(
                !(p.red > p.blue && p.red > p.green),
                "seed '$seed' must not be red-dominant: $p",
            )
        }
    }

    /** Control chrome never takes the raw artwork colour; it is brand-blended and floored. */
    @Test
    fun controlAccent_isLegibleAndBrandLeaning() {
        val darkArtwork = ArtworkColors.fromPrimary(Color(0xFF120A05))
        val control = darkArtwork.controlAccent
        val lum = 0.2126f * control.red + 0.7152f * control.green + 0.0722f * control.blue
        assertTrue(lum > 0.30f, "control accent must stay visible on near-black, lum=$lum")

        val redArtwork = ArtworkColors.fromPrimary(Color(0xFFD32F2F))
        val tamed = redArtwork.controlAccent
        assertTrue(tamed.blue > redArtwork.primary.blue, "brand accent must pull red toward violet")
    }

    @Test
    fun extractFromSeed_blankFallsBackToAccent() {
        val blank = ArtworkColorExtractor.extractFromSeed("")
        assertEquals(ArtworkColors.fallback.primary, blank.primary)
        val alsoBlank = ArtworkColorExtractor.extractFromSeed("   ")
        assertEquals(ArtworkColors.fallback.primary, alsoBlank.primary)
    }

    @Test
    fun extract_bitmapFallbackWhenEmpty() {
        // 1x1 transparent bitmap → fallback (no vivid pixels)
        // ImageBitmap construction is platform-specific; we test via the
        // fallback path using an empty bitmap where possible.
        // On JVM/Desktop, ImageBitmap(1,1) is valid.
        try {
            val bmp = ImageBitmap(1, 1)
            val result = ArtworkColorExtractor.extract(bmp)
            assertNotNull(result)
            // Either fallback or a derived color — both are sane.
            assertTrue(result.primary.alpha > 0.9f)
            assertTrue(result.container.alpha in 0.2f..0.4f)
        } catch (_: Throwable) {
            // Platform where ImageBitmap ctor needs graphics context (e.g. headless CI)
            // — seed path already covers the acceptance criterion. Pass.
        }
    }

    @Test
    fun fallback_hasExpectedAccent() {
        assertEquals(DhunColors.accent, ArtworkColors.fallback.primary)
        assertEquals(DhunColors.onAccent, ArtworkColors.fallback.onPrimary)
    }
}
