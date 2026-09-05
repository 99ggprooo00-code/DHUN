package dev.dhun.design

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LocalContentColor
import kotlin.math.min

/**
 * Lightweight icon set for DHUN.
 *
 * The paths are the 24dp Material Design icon paths, embedded here so the UI
 * has no icon-art dependency or binary asset payload. Material Design icons
 * are Apache-2.0 licensed; the attribution is recorded in THIRD_PARTY.md.
 */
enum class DhunIcon(val pathData: String) {
    Home("M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"),
    Search("M9.5 3C5.91 3 3 5.91 3 9.5C3 13.09 5.91 16 9.5 16C11.1 16 12.56 15.42 13.68 14.45L19.49 20.26L20.9 18.85L15.1 13.04C15.68 12.03 16 10.82 16 9.5C16 5.91 13.09 3 9.5 3ZM9.5 5C11.99 5 14 7.01 14 9.5C14 11.99 11.99 14 9.5 14C7.01 14 5 11.99 5 9.5C5 7.01 7.01 5 9.5 5Z"),
    LibraryMusic("M4 5h10v2H4V5z M4 9h10v2H4V9z M4 13h6v2H4v-2z M16 7v8.5c-.43-.31-.94-.5-1.5-.5C13.12 15 12 16.12 12 17.5S13.12 20 14.5 20s2.5-1.12 2.5-2.5V10h3V7h-4z"),
    Palette("M12 3C7.03 3 3 6.58 3 11s4.03 8 9 8c.83 0 1.5-.67 1.5-1.5 0-.39-.15-.75-.39-1.02-.23-.27-.37-.63-.37-1.03 0-.8.65-1.45 1.45-1.45H16c2.76 0 5-2.24 5-5C21 6.58 16.97 3 12 3z M7.5 11C6.67 11 6 10.33 6 9.5S6.67 8 7.5 8 9 8.67 9 9.5 8.33 11 7.5 11z M11.5 8C10.67 8 10 7.33 10 6.5S10.67 5 11.5 5 13 5.67 13 6.5 12.33 8 11.5 8z M15.5 9C14.67 9 14 8.33 14 7.5S14.67 6 15.5 6 17 6.67 17 7.5 16.33 9 15.5 9z M17 13C16.17 13 15.5 12.33 15.5 11.5S16.17 10 17 10s1.5.67 1.5 1.5S17.83 13 17 13z"),
    Shuffle("M4 5h2.5l11 11H20v2h-3.33L5.67 7H4V5z M16 5h1.5L20 7.5V5h2v6h-2V8.33L16 5z M4 17h1.67l3.5-3.5 1.42 1.42L6.5 19H4v-2z"),
    SkipPrevious("M6 6v12l8.5-6L6 6z M15 6v12h2V6h-2z"),
    Pause("M6 4h4v16H6V4z M14 4h4v16h-4V4z"),
    Play("M8 5v14l11-7L8 5z"),
    SkipNext("M18 6v12l-8.5-6L18 6z M7 6v12h2V6H7z"),
    Repeat("M17 1l4 4-4 4V6H7C5.34 6 4 7.34 4 9H2c0-2.76 2.24-5 5-5h10V1z M7 18h10c1.66 0 3-1.34 3-3h2c0 2.76-2.24 5-5 5H7v4l-4-4 4-4v2z"),
    RepeatOne("M17 1l4 4-4 4V6H7C5.34 6 4 7.34 4 9H2c0-2.76 2.24-5 5-5h10V1z M7 18h10c1.66 0 3-1.34 3-3h2c0 2.76-2.24 5-5 5H7v4l-4-4 4-4v2z M13 11h-1v-1h2v6h-1v-5z"),
    VolumeUp("M3 9v6h4l5 5V4L7 9H3z M16.5 12C16.5 10.23 15.5 8.71 14 7.97v8.05c1.5-.73 2.5-2.25 2.5-4.02z M14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"),
    Add("M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"),
    QueueMusic("M15 6H3v2h12V6z M15 10H3v2h12v-2z M3 14h8v2H3v-2z M17 6v8.5c-.43-.31-.94-.5-1.5-.5C14.12 14 13 15.12 13 16.5s1.12 2.5 2.5 2.5 2.5-1.12 2.5-2.5V9h3V6h-4z"),
    Favorite("M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5C2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3C19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"),
    FavoriteBorder("M16.5 3c-1.74 0-3.41.81-4.5 2.09C10.91 3.81 9.24 3 7.5 3C4.42 3 2 5.42 2 8.5c0 3.78 3.4 6.86 8.55 11.54L12 21.35l1.45-1.32C18.6 15.36 22 12.28 22 8.5C22 5.42 19.58 3 16.5 3z M12.1 18.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5C4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z"),
    Person("M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4z M12 14c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"),
    Album("M12 2C6.48 2 2 6.48 2 12C2 17.52 6.48 22 12 22C17.52 22 22 17.52 22 12C22 6.48 17.52 2 12 2z M12 6C8.69 6 6 8.69 6 12C6 15.31 8.69 18 12 18C15.31 18 18 15.31 18 12C18 8.69 15.31 6 12 6z M12 9C10.34 9 9 10.34 9 12C9 13.66 10.34 15 12 15C13.66 15 15 13.66 15 12C15 10.34 13.66 9 12 9z"),
    ArrowBack("M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"),
    MoreVert("M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2z M12 14c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2z M12 20c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2z"),
    Refresh("M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"),
    Close("M18.3 5.71L12 12l6.3 6.29-1.41 1.42L10.59 13.41 4.3 19.71 2.89 18.29 9.17 12 2.89 5.71 4.3 4.29l6.29 6.3 6.3-6.3 1.41 1.42z"),
    History("M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.78-4.95-2.05l-1.42 1.42C8.27 20.01 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9z M12 7v5l4 2 .75-1.23-3.25-1.77V7H12z"),
    Error("M12 2C6.48 2 2 6.48 2 12C2 17.52 6.48 22 12 22C17.52 22 22 17.52 22 12C22 6.48 17.52 2 12 2z M13 17h-2v-2h2v2z M13 13h-2V7h2v6z"),
}

private const val VIEWBOX_SIZE = 24f
private val SVG_TOKEN = Regex("[A-Za-z]|[-+]?(?:\\d*\\.?\\d+)(?:[eE][-+]?\\d+)?")

/** Minimal SVG path parser for the embedded 24dp Material paths. */
fun Path.Companion.fromSvg(pathData: String): Path = SvgPathParser(pathData).parse()

private class SvgPathParser(pathData: String) {
    private val tokens = SVG_TOKEN.findAll(pathData).map { it.value }.toList()
    private var index = 0
    private var command = ' '
    private var previousCommand = ' '
    private var x = 0f
    private var y = 0f
    private var startX = 0f
    private var startY = 0f
    private var lastCubicX = 0f
    private var lastCubicY = 0f
    private var lastQuadX = 0f
    private var lastQuadY = 0f

    fun parse(): Path {
        val path = Path()
        while (index < tokens.size) {
            if (isCommand()) command = tokens[index++].first()
            if (command == ' ' || command == 'Z' || command == 'z') {
                if (command == 'Z' || command == 'z') {
                    path.close(); x = startX; y = startY; previousCommand = command; command = ' '
                }
                continue
            }
            when (command) {
                'M', 'm' -> move(path, relative = command == 'm')
                'L', 'l' -> line(path, relative = command == 'l')
                'H', 'h' -> horizontal(path, relative = command == 'h')
                'V', 'v' -> vertical(path, relative = command == 'v')
                'C', 'c' -> cubic(path, relative = command == 'c')
                'S', 's' -> smoothCubic(path, relative = command == 's')
                'Q', 'q' -> quadratic(path, relative = command == 'q')
                'T', 't' -> smoothQuadratic(path, relative = command == 't')
                'A', 'a' -> arcAsLine(path, relative = command == 'a')
                else -> index++
            }
        }
        return path
    }

    private fun isCommand(): Boolean = index < tokens.size && tokens[index].length == 1 && tokens[index][0].isLetter()
    private fun has(count: Int): Boolean = index + count <= tokens.size && (index until index + count).none { isCommandAt(it) }
    private fun isCommandAt(position: Int): Boolean = tokens[position].length == 1 && tokens[position][0].isLetter()
    private fun number(): Float = tokens[index++].toFloat()
    private fun point(px: Float, py: Float, relative: Boolean): Pair<Float, Float> =
        if (relative) (x + px) to (y + py) else px to py
    private fun resetControls() { lastCubicX = x; lastCubicY = y; lastQuadX = x; lastQuadY = y }

    private fun move(path: Path, relative: Boolean) {
        if (!has(2)) { command = ' '; return }
        val (nx, ny) = point(number(), number(), relative)
        path.moveTo(nx, ny); x = nx; y = ny; startX = x; startY = y; resetControls(); previousCommand = command
        command = if (relative) 'l' else 'L'
    }

    private fun line(path: Path, relative: Boolean) {
        if (!has(2)) { command = ' '; return }
        val (nx, ny) = point(number(), number(), relative)
        path.lineTo(nx, ny); x = nx; y = ny; resetControls(); previousCommand = command
    }

    private fun horizontal(path: Path, relative: Boolean) {
        if (!has(1)) { command = ' '; return }
        x = if (relative) x + number() else number(); path.lineTo(x, y); resetControls(); previousCommand = command
    }

    private fun vertical(path: Path, relative: Boolean) {
        if (!has(1)) { command = ' '; return }
        y = if (relative) y + number() else number(); path.lineTo(x, y); resetControls(); previousCommand = command
    }

    private fun cubic(path: Path, relative: Boolean) {
        if (!has(6)) { command = ' '; return }
        val (cx1, cy1) = point(number(), number(), relative)
        val (cx2, cy2) = point(number(), number(), relative)
        val (nx, ny) = point(number(), number(), relative)
        path.cubicTo(cx1, cy1, cx2, cy2, nx, ny); lastCubicX = cx2; lastCubicY = cy2; x = nx; y = ny; previousCommand = command
    }

    private fun smoothCubic(path: Path, relative: Boolean) {
        if (!has(4)) { command = ' '; return }
        val (cx2, cy2) = point(number(), number(), relative)
        val (nx, ny) = point(number(), number(), relative)
        val (cx1, cy1) = if (previousCommand == 'C' || previousCommand == 'c' || previousCommand == 'S' || previousCommand == 's') {
            (2 * x - lastCubicX) to (2 * y - lastCubicY)
        } else x to y
        path.cubicTo(cx1, cy1, cx2, cy2, nx, ny); lastCubicX = cx2; lastCubicY = cy2; x = nx; y = ny; previousCommand = command
    }

    private fun quadratic(path: Path, relative: Boolean) {
        if (!has(4)) { command = ' '; return }
        val (cx, cy) = point(number(), number(), relative)
        val (nx, ny) = point(number(), number(), relative)
        path.quadraticTo(cx, cy, nx, ny); lastQuadX = cx; lastQuadY = cy; x = nx; y = ny; previousCommand = command
    }

    private fun smoothQuadratic(path: Path, relative: Boolean) {
        if (!has(2)) { command = ' '; return }
        val (nx, ny) = point(number(), number(), relative)
        val (cx, cy) = if (previousCommand == 'Q' || previousCommand == 'q' || previousCommand == 'T' || previousCommand == 't') {
            (2 * x - lastQuadX) to (2 * y - lastQuadY)
        } else x to y
        path.quadraticTo(cx, cy, nx, ny); lastQuadX = cx; lastQuadY = cy; x = nx; y = ny; previousCommand = command
    }

    private fun arcAsLine(path: Path, relative: Boolean) {
        if (!has(7)) { command = ' '; return }
        repeat(5) { number() }
        val (nx, ny) = point(number(), number(), relative)
        path.lineTo(nx, ny); x = nx; y = ny; resetControls(); previousCommand = command
    }
}

/** Draws one embedded icon in a 24dp viewbox with optional accessibility text. */
@Composable
fun DhunIconView(
    icon: DhunIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val path = remember(icon) { Path.fromSvg(icon.pathData) }
    val accessibleModifier = if (contentDescription == null) {
        modifier
    } else {
        modifier.semantics { this.contentDescription = contentDescription }
    }
    Canvas(modifier = accessibleModifier) {
        val scale = min(size.width, size.height) / VIEWBOX_SIZE
        withTransform({
            translate(
                left = (size.width - VIEWBOX_SIZE * scale) / 2f,
                top = (size.height - VIEWBOX_SIZE * scale) / 2f,
            )
            scale(scale, scale)
        }) {
            drawPath(path = path, color = tint, style = Fill)
        }
    }
}
