package dev.dhun.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.use
import androidx.compose.ui.unit.Density
import dev.dhun.design.components.NowPlayingBackdropLayer
import dev.dhun.design.components.NowPlayingBackdropPolicy
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/** Pixel-level Compose/Skia coverage of the production artwork+veil layer. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class NowPlayingBackdropRenderTest {

    @Test
    fun lightBlurOffArtworkIsVisibleButVeiledOverTheWarmBase() {
        val base = renderPixel(artwork = null)
        val blackCover = renderPixel(artwork = Color.Black)
        val whiteCover = renderPixel(artwork = Color.White)

        // Sample an unobstructed point between Home's quick actions and section
        // heading, using the same fixed viewport as the app-shell evidence.
        val baseLevel = (base.red + base.green + base.blue) / 3
        val blackLevel = (blackCover.red + blackCover.green + blackCover.blue) / 3
        val whiteDistance = kotlin.math.abs(base.red - whiteCover.red) +
            kotlin.math.abs(base.green - whiteCover.green) + kotlin.math.abs(base.blue - whiteCover.blue)

        assertTrue(
            baseLevel - blackLevel in 20..90,
            "Black artwork should remain visible but veiled, not disappear or become a black slab: base=$base cover=$blackCover",
        )
        assertTrue(
            whiteDistance <= 24,
            "White artwork should blend into the warm light base rather than wash it out: base=$base cover=$whiteCover",
        )
    }

    private fun renderPixel(artwork: Color?): java.awt.Color {
        DhunAppearance.reset()
        DhunVisualTuning.restore(emptyMap(), enabled = false)
        try {
            ImageComposeScene(
                width = 412,
                height = 915,
                density = Density(1f),
                content = {
                    DhunTheme(mode = DhunThemeMode.LIGHT) {
                        Box(Modifier.fillMaxSize().background(DhunColors.background)) {
                            if (artwork != null) {
                                NowPlayingBackdropLayer(
                                    artworkAlpha = NowPlayingBackdropPolicy.loadedLayerAlpha(
                                        isLight = true,
                                        blurActive = false,
                                    ),
                                    veilTint = NowPlayingBackdropPolicy.veilTint(DhunTokens.light),
                                    veilStops = NowPlayingBackdropPolicy.veilStops(
                                        isLight = true,
                                        brightnessPercent = 55,
                                    ).map { (offset, alpha) ->
                                        offset to DhunVisualTuning.veilAlpha(VisualPart.PAGE_BACKDROP, alpha)
                                    },
                                ) {
                                    Box(Modifier.fillMaxSize().background(artwork))
                                }
                            }
                        }
                    }
                },
            ).use { scene ->
                scene.render(0L).close()
                return scene.render(32_000_000L).use { rendered ->
                    val bitmap = rendered.encodeToData()!!.use { encoded ->
                        checkNotNull(ImageIO.read(ByteArrayInputStream(encoded.bytes))) {
                            "Compose/Skia did not encode the backdrop probe frame"
                        }
                    }
                    try {
                        java.awt.Color(bitmap.getRGB(200, 190), true)
                    } finally {
                        bitmap.flush()
                    }
                }
            }
        } finally {
            DhunAppearance.reset()
            DhunVisualTuning.restore(emptyMap(), enabled = false)
        }
    }
}
