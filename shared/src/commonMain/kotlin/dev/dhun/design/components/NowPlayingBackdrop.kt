package dev.dhun.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.dhun.design.ArtworkUrls
import dev.dhun.design.DhunAnimations
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunSpacing
import dev.dhun.design.supportsRealtimeBlur

/**
 * The atmospheric backdrop behind Home, Search and Library: the artwork of the
 * **currently playing** song, heavily blurred and darkened, edge to edge, under
 * everything else.
 *
 * ## Source of truth
 *
 * It takes one nullable artwork URL and nothing else. The shell passes the
 * global playback state it already observes (`PlayerViewModel.currentTrack`),
 * so the backdrop follows playback exactly and there is no second
 * "currently playing" state: change the track and the backdrop changes with it,
 * on whichever screen is showing. What a screen is *listing* — search results,
 * a library row, the item under the finger — is never the source.
 *
 * ## Fallback rule
 *
 * The default DHUN background is the fallback, not a hole. Nothing is drawn at
 * all when the current song has no thumbnail, the URL is blank, the load fails,
 * or the platform cannot really blur ([supportsRealtimeBlur]) — in every one of
 * those cases the existing background shows through untouched, and no
 * placeholder, glyph, grey rectangle or spinner is ever stretched across a
 * screen.
 *
 * ## Cost
 *
 * One Coil request per track change at the *list* tier
 * ([NowPlayingBackdropPolicy.artworkSizePx], 544px), not the 1024px
 * now-playing tier: a background blurred by 64dp has no use for the extra
 * pixels. Coil's own `crossfade` provides the old→new dissolve, so a track
 * change is a fade rather than a hard swap, and nothing animates per frame —
 * the blur sits on its own static layer while the content above it scrolls.
 */
@Composable
fun NowPlayingBackdrop(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    dimColor: Color = Color.Black.copy(alpha = NowPlayingBackdropPolicy.DIM_ALPHA),
) {
    if (!NowPlayingBackdropPolicy.shouldRenderBackdrop(artworkUrl, supportsRealtimeBlur)) return
    val url = remember(artworkUrl) { NowPlayingBackdropPolicy.resolveUrl(artworkUrl) } ?: return

    val context = LocalPlatformContext.current
    // `phase` restarts per URL; `loadedOnce` deliberately does not, so the
    // previous artwork stays on screen while the next one loads and Coil
    // crossfades it in — no flash of the default background between tracks.
    var phase by remember(artworkUrl) { mutableStateOf(NowPlayingBackdropPhase.Idle) }
    var loadedOnce by remember { mutableStateOf(false) }
    val artworkAlpha by animateFloatAsState(
        targetValue = if (loadedOnce && phase != NowPlayingBackdropPhase.Failed) 1f else 0f,
        animationSpec = DhunAnimations.slowTween(),
        label = "nowPlayingBackdrop",
    )

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Overscaled past the edges so the blurred rim is never
                    // visible — the same trick FullPlayer's bleed uses.
                    scaleX = NowPlayingBackdropPolicy.OVERSCAN
                    scaleY = NowPlayingBackdropPolicy.OVERSCAN
                    alpha = artworkAlpha
                }
                .blur(DhunSpacing.glassBlur * NowPlayingBackdropPolicy.BLUR_SCALE),
            contentScale = ContentScale.Crop,
            onLoading = { phase = NowPlayingBackdropPhase.Loading },
            onSuccess = {
                loadedOnce = true
                phase = NowPlayingBackdropPhase.Loaded
            },
            onError = { phase = NowPlayingBackdropPhase.Failed },
        )
        // Darken, then re-tint with the DHUN background colour so the artwork
        // reads as atmosphere in the app's own palette instead of as a photo.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(dimColor),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = NowPlayingBackdropPolicy.scrimStops()
                            .map { (offset, alpha) ->
                                offset to DhunColors.background.copy(alpha = alpha)
                            }
                            .toTypedArray(),
                    ),
                ),
        )
    }
}

/**
 * A detail page's own backdrop: the **same** blurred-and-darkened artwork
 * treatment as [NowPlayingBackdrop], pointed at the page's subject (an album
 * cover, an artist portrait) rather than at the playing track.
 *
 * Album and Artist pages used to paint an opaque `DhunColors.background` over
 * themselves. That hid the shell backdrop completely and left the page a flat
 * colour wash (an [dev.dhun.design.ArtworkColorExtractor] seed tint) while
 * Home / Search / Library glowed with real artwork. Those pages now stay
 * transparent and paint this instead — including while nothing is playing,
 * which is exactly when the shell backdrop has nothing to show.
 *
 * It is a wrapper on purpose: one recipe, one set of numbers
 * ([NowPlayingBackdropPolicy] — list-tier request, 64dp blur, dim and scrim),
 * so a page backdrop can never drift away from the shell's, and a page with no
 * artwork (or a platform that cannot really blur) draws nothing at all and
 * leaves the shell backdrop / base colour as the fallback.
 */
@Composable
fun PageArtworkBackdrop(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
) {
    NowPlayingBackdrop(artworkUrl = artworkUrl, modifier = modifier)
}

/** Load lifecycle of the backdrop's artwork request. */
enum class NowPlayingBackdropPhase { Idle, Loading, Loaded, Failed }

/**
 * Backdrop decisions kept pure so `NowPlayingBackdropPolicyTest` pins the
 * fallback rule instead of leaving it to a device screenshot.
 */
object NowPlayingBackdropPolicy {

    /**
     * Flat black laid over the blur: low contrast, text stays readable.
     * Lowered 0.55 → 0.45 by the 2026-09-21 polish, then 0.45 → 0.40 so
     * Home / Search / Library sit closer to the full player's artwork glow.
     * 0.40 is the light end of the 0.4–0.75 band pinned by
     * `NowPlayingBackdropPolicyTest` — do not go under it.
     */
    const val DIM_ALPHA = 0.40f

    /** Blur radius, as a multiple of [DhunSpacing.glassBlur] (16dp → 64dp). */
    const val BLUR_SCALE = 4

    /** Scale past the screen edges so the blurred rim never shows. */
    const val OVERSCAN = 1.15f

    /** Artwork tier this backdrop asks for — the list tier, see [resolveUrl]. */
    val artworkSizePx: Int get() = ArtworkUrls.LIST_SIZE_PX

    /**
     * Whether a backdrop should be rendered given the thumbnail URL and
     * platform blur capability.
     */
    fun shouldRenderBackdrop(thumbnailUrl: String?, supportsBlur: Boolean): Boolean =
        resolveUrl(thumbnailUrl) != null && supportsBlur

    /**
     * Artwork to request, or `null` when the current song has nothing usable —
     * `null` means "draw no backdrop at all", which is what keeps the default
     * DHUN background as the fallback.
     *
     * Uses the **list** tier (544px), never [ArtworkUrls.nowPlaying]: a
     * background blurred by 64dp cannot resolve 1024px, and three screens
     * should not each pull a megapixel image.
     */
    fun resolveUrl(thumbnailUrl: String?): String? {
        if (thumbnailUrl.isNullOrBlank()) return null
        return ArtworkUrls.list(thumbnailUrl, artworkSizePx)
    }

    /**
     * Background-colour scrim over the blur (`offset` to `alpha`). Heaviest at
     * the top and bottom — where the search field, section headers and the
     * docked mini-player sit — and lightest across the middle so the artwork
     * still glows through. Every stop is well below opaque: the artwork is a
     * wash, never a photo.
     *
     * Lowered 0.62/0.42/0.58/0.78 → 0.50/0.32/0.44/0.62 so the shell screens
     * read closer to the full player's backdrop. The shape (edges heavier than
     * the middle, nothing opaque) is pinned by `NowPlayingBackdropPolicyTest`.
     */
    fun scrimStops(): List<Pair<Float, Float>> = listOf(
        0.00f to 0.50f,
        0.35f to 0.32f,
        0.72f to 0.44f,
        1.00f to 0.62f,
    )
}
