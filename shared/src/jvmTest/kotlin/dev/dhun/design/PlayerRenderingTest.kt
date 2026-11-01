package dev.dhun.design

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerRenderingTest {
    private data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /** Exercise the SAME DrawScope function as DhunIconView, not a second SVG renderer. */
    private fun bounds(icon: DhunIcon, width: Int, height: Int): Bounds {
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(Color.Transparent, blendMode = BlendMode.Clear)
            drawDhunIcon(Path.fromSvg(icon.pathData), Color.White)
        }
        val pixels = bitmap.toPixelMap()
        val points = buildList {
            for (y in 0 until height) for (x in 0 until width) {
                if (pixels[x, y].alpha > 0.1f) add(x to y)
            }
        }
        assertTrue(points.isNotEmpty(), "$icon rendered no pixels")
        return Bounds(points.minOf { it.first }, points.minOf { it.second }, points.maxOf { it.first } + 1, points.maxOf { it.second } + 1)
    }

    @Test
    fun transportIconsKeepTheirPositionAtDifferentSizesAndDensities() {
        for (icon in listOf(DhunIcon.Shuffle, DhunIcon.SkipPrevious, DhunIcon.Play, DhunIcon.Pause, DhunIcon.SkipNext, DhunIcon.Repeat, DhunIcon.RepeatOne)) {
            val normal = bounds(icon, 24, 24)
            for (size in listOf(18, 30, 32, 36, 48, 64)) {
                val scaled = bounds(icon, size, size)
                val factor = size / 24f
                val expected = listOf(normal.left, normal.top, normal.right, normal.bottom).map { it * factor }
                val actual = listOf(scaled.left, scaled.top, scaled.right, scaled.bottom)
                expected.zip(actual).forEach { (e, a) ->
                    assertTrue(abs(e - a) <= 1.5f, "$icon at $size px moved/clipped: expected $expected, got $actual")
                }
            }
        }
    }

    @Test
    fun nonSquareIconCanvasIsCentred() {
        val normal = bounds(DhunIcon.Pause, 48, 48)
        val wide = bounds(DhunIcon.Pause, 80, 48)
        assertEquals(normal.copy(left = normal.left + 16, right = normal.right + 16), wide)
        val tall = bounds(DhunIcon.Pause, 48, 80)
        assertEquals(normal.copy(top = normal.top + 16, bottom = normal.bottom + 16), tall)
    }

    @Test
    fun artworkBoxConsumesItsMeasuredHeroFieldUntilTheDesktopCeiling() {
        // Square art: the box is a square capped by both axes and the token.
        assertEquals(180.dp, fittedPlayerArtworkBox(1_400.dp, 180.dp, 1f).width)
        assertEquals(280.dp, fittedPlayerArtworkBox(280.dp, 600.dp, 1f).width)
        assertEquals(640.dp, DhunSpacing.playerArtworkMaxSize, "desktop/tablet ceiling must stay intentionally large")
        assertEquals(DhunSpacing.playerArtworkMaxSize, fittedPlayerArtworkBox(1_400.dp, 800.dp, 1f).width)
        assertEquals(DhunSpacing.playerArtworkMaxSize, fittedPlayerArtworkBox(688.dp, 760.dp, 1f).width)
        assertEquals(0.dp, fittedPlayerArtworkBox(800.dp, 0.dp, 1f).width)
        // Wide art on a phone: full-width band whose height follows the aspect.
        val band = fittedPlayerArtworkBox(393.dp, 520.dp, 16f / 9f)
        assertEquals(393.dp, band.width)
        assertTrue(abs(band.height.value - 393f * 9f / 16f) <= 0.01f, "band height ${band.height} must follow 16:9")
        // Tall art: height-limited, centred by the caller.
        val tall = fittedPlayerArtworkBox(393.dp, 520.dp, 0.5f)
        assertEquals(260.dp, tall.width)
        assertEquals(520.dp, tall.height)
    }
}
