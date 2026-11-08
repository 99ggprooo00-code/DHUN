package dev.dhun.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.use
import dev.dhun.design.DhunAccent
import dev.dhun.design.DhunAppearance
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunSpacing
import dev.dhun.design.DhunTheme
import dev.dhun.design.DhunThemeMode
import dev.dhun.design.DhunTypographyTokens
import dev.dhun.design.components.DhunButton
import dev.dhun.design.components.DhunConfirmDialog
import dev.dhun.design.components.DhunNavigationBar
import dev.dhun.design.components.DhunNavigationBarItem
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.GlassCard
import dev.dhun.design.components.GlassDialog
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Real Compose/Skia frames for visual acceptance, not a second design mockup.
 * CI uploads the PNGs as ordinary artifacts and emits a small Git-blob manifest
 * so environments that cannot download Actions artifacts can still inspect the
 * exact rendered output. This covers shared primitives; the broader app-shell
 * and state matrix, including pixel probes, lives in VisualAppEvidenceTest.
 */
@OptIn(ExperimentalComposeUiApi::class)
class VisualEvidenceTest {
    private val output = File("build/outputs/visual-evidence").apply { mkdirs() }

    @Test
    fun `render DHUN glass primitives in dark and light modes`() {
        DhunAppearance.reset()
        try {
            for (mode in listOf(DhunThemeMode.DARK, DhunThemeMode.LIGHT)) {
                DhunAppearance.setAppearance(nextMode = mode, nextAccent = DhunAccent.BRAND)
                val suffix = mode.id
                render("components-$suffix.png", width = 1200, height = 780) {
                    DhunTheme(mode = mode, accent = DhunAccent.BRAND) {
                        ComponentFrame()
                    }
                }
                var inheritedContentColor: Color? = null
                render("dialog-$suffix.png", width = 600, height = 500) {
                    DhunTheme(mode = mode, accent = DhunAccent.BRAND) {
                        Box(Modifier.fillMaxSize().background(DhunColors.background))
                        GlassDialog(onDismissRequest = {}) {
                            inheritedContentColor = LocalContentColor.current
                            Text(
                                "Playlist changes",
                                style = DhunTypographyTokens.titleLarge,
                                color = DhunColors.textPrimary,
                            )
                            Text(
                                "This body text inherits DHUN's readable foreground automatically.",
                                modifier = Modifier.padding(top = DhunSpacing.md),
                            )
                            Text(
                                "The existing actions and dismissal behavior are unchanged.",
                                color = DhunColors.textSecondary,
                                modifier = Modifier.padding(top = DhunSpacing.sm),
                            )
                            DhunButton(
                                onClick = {},
                                modifier = Modifier.fillMaxWidth().padding(top = DhunSpacing.lg),
                            ) {
                                Text("Keep playlist")
                            }
                            DhunTextButton(
                                onClick = {},
                                modifier = Modifier.fillMaxWidth().padding(top = DhunSpacing.sm),
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
                assertEquals(
                    DhunColors.textPrimary,
                    inheritedContentColor,
                    "$suffix GlassDialog should provide the active DHUN foreground by default",
                )
                render("confirm-dialog-$suffix.png", width = 412, height = 915) {
                    DhunTheme(mode = mode, accent = DhunAccent.BRAND) {
                        Box(Modifier.fillMaxSize().background(DhunColors.background))
                        DhunConfirmDialog(
                            title = "Clear lyrics cache?",
                            message = "Automatic lyrics will be fetched again. Saved per-song choices remain; use Use automatic in the lyrics match picker to reset one.",
                            confirmLabel = "Clear",
                            pendingLabel = "Clearing…",
                            onConfirm = {},
                            onDismissRequest = {},
                        )
                    }
                }
            }
            val frames = output.listFiles().orEmpty().filter { it.extension == "png" }
            assertTrue(frames.size >= 4, "Expected real PNG frames, found ${frames.map { it.name }}")
            assertTrue(frames.all { it.length() > 1_000 }, "A rendered frame was empty or implausibly small")
        } finally {
            DhunAppearance.reset()
        }
    }

    @Composable
    private fun ComponentFrame() {
        Column(Modifier.fillMaxSize().background(DhunColors.background)) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(DhunSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(DhunSpacing.lg),
            ) {
                Text("DHUN visual evidence", style = DhunTypographyTokens.brand, color = DhunColors.accent)
                GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(DhunSpacing.xl)) {
                    Column(verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm)) {
                        Text("Frosted reading surface", style = DhunTypographyTokens.titleLarge, color = DhunColors.textPrimary)
                        Text(
                            "Real Compose rendering of the shared glass body, edge, sheen, typography and controls.",
                            color = DhunColors.textSecondary,
                        )
                        DhunButton(onClick = {}, modifier = Modifier.padding(top = DhunSpacing.md)) {
                            Text("Play")
                        }
                    }
                }
            }
            DhunNavigationBar {
                DhunNavigationBarItem(icon = DhunIcon.Home, label = "Home", selected = true, onClick = {})
                DhunNavigationBarItem(icon = DhunIcon.Search, label = "Search", selected = false, onClick = {})
                DhunNavigationBarItem(icon = DhunIcon.LibraryMusic, label = "Library", selected = false, onClick = {})
            }
        }
    }

    private fun render(name: String, width: Int, height: Int, content: @Composable () -> Unit) {
        ImageComposeScene(width = width, height = height, content = content).use { scene ->
            // Advance animation clocks and let composition/layout/draw settle.
            repeat(24) { frame -> scene.render(frame * 16_000_000L).close() }
            scene.render(500_000_000L).use { image ->
                image.encodeToData()!!.use { encoded ->
                    File(output, name).writeBytes(encoded.bytes)
                }
            }
        }
    }
}
