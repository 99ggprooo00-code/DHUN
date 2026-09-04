package dev.dhun.desktop.native

import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * Phase 12 — programmatic tray icons (no binary assets in the repo; the
 * jpackage step swaps in a real .ico when one is available).
 *
 * 32×32 ARGB by default (Windows scales via `setAutoSize`). Dark rounded
 * tile + DHUN accent glyph: play triangle while playing, pause bars while
 * paused/idle.
 */
object TrayIcons {

    private val background = Color(0xFF161616)
    private val border = Color(0x33FFFFFF)
    private val accent = Color(0xFFBB86FC)

    fun playing(size: Int = 32): BufferedImage = draw(size) { g, s ->
        tile(g, s)
        g.color = accent
        val left = (s * 0.36f).toInt()
        val right = (s * 0.68f).toInt()
        val h = (s * 0.44f).toInt()
        val top = (s - h) / 2
        g.fillPolygon(
            intArrayOf(left, left, right),
            intArrayOf(top, top + h, top + h / 2),
            3,
        )
    }

    fun paused(size: Int = 32): BufferedImage = draw(size) { g, s ->
        tile(g, s)
        g.color = accent
        val barW = (s * 0.12f).coerceAtLeast(1)
        val gap = (s * 0.09f).coerceAtLeast(1)
        val h = (s * 0.40f).toInt()
        val top = (s - h) / 2
        val mid = s / 2
        g.fillRect(mid - gap - barW, top, barW, h)
        g.fillRect(mid + gap, top, barW, h)
    }

    private fun tile(g: Graphics2D, s: Int) {
        val arc = (s * 0.24f).toInt().coerceAtLeast(2)
        g.color = background
        g.fillRoundRect(0, 0, s - 1, s - 1, arc, arc)
        g.color = border
        g.drawRoundRect(0, 0, s - 1, s - 1, arc, arc)
    }

    private inline fun draw(size: Int, block: (Graphics2D, Int) -> Unit): BufferedImage {
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            block(g, size)
        } finally {
            g.dispose()
        }
        return img
    }
}
