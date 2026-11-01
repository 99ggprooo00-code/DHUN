package dev.dhun.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
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
import dev.dhun.design.components.DhunNavigationBar
import dev.dhun.design.components.DhunNavigationBarItem
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.GlassCard
import dev.dhun.design.components.GlassDialog
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Real Compose/Skia frames for visual acceptance, not a second design mockup.
 * CI uploads the PNGs as ordinary artifacts and emits a small Git-blob manifest
 * so environments that cannot download Actions artifacts can still inspect the
 * exact rendered output. These initial frames prove the evidence path; the
 * screen/state matrix is added only after that path is known to work.
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
                render("dialog-$suffix.png", width = 600, height = 500) {
                    DhunTheme(mode = mode, accent = DhunAccent.BRAND) {
                        Box(Modifier.fillMaxSize().background(DhunColors.background))
                        GlassDialog(onDismissRequest = {}) {
                            Text(
                                "Playlist changes",
                                style = DhunTypographyTokens.titleLarge,
                                color = DhunColors.textPrimary,
                            )
                            Text(
                                "The existing dialog keeps its actions and dismissal behavior while using DHUN glass.",
                                color = DhunColors.textSecondary,
                                modifier = Modifier.padding(top = DhunSpacing.md),
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
