package dev.dhun.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import dev.dhun.design.DhunAppearance
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.VisualPart
import dev.dhun.design.background.BackgroundComponent
import dev.dhun.design.background.DhunBackgrounds
import dev.dhun.design.background.LocalBackgroundComponent

/**
 * The numbers of the DHUN glass material — the one recipe every translucent
 * surface in the app is painted with, so a card, a dialog and a menu can never
 * drift into three different "glass" looks (or into none at all).
 *
 * **The defect this replaces.** [GlassCard] used to paint a single flat
 * `readingSurfaceFill()` rectangle with a 1dp border and ignore its own
 * `blurRadius`. Every dialog, every Settings group and every legal card was
 * therefore an ordinary opaque slab — "glass" was a variable name, not a
 * material. This policy makes the material explicit and testable:
 *
 * 1. **Body depth** — a vertical gradient from a lifted top to a deepened
 *    bottom, both derived from the same readable base fill. A glass panel is
 *    never one flat colour.
 * 2. **Specular sheen** — a white highlight along the top edge, fading out by
 *    [SHEEN_EXTENT]. A static cue, not per-frame blur.
 * 3. **Glass edge** — a *gradient* hairline, bright where light would hit
 *    (top) and nearly gone at the bottom, instead of a uniform stroke that
 *    reads as a Material outline.
 * 4. **Frosted artwork (frost cut only)** — dialogs and menus paint the
 *    now-playing cover, blurred once and cached, under the body, so the panel
 *    is visibly glass over artwork instead of a flat plate over a dim scrim.
 *
 * Legibility is never traded for the effect: the body opacity stays in the
 * band the contrast gates measure (`docs/ui-ux-design.md` rule 1), lift and
 * deepen are small, and the sheen is capped. A platform that cannot blur still
 * shows gradient + sheen + edge — glass, without washed-out text.
 */
object GlassMaterialPolicy {

    /** White added to the top of the body gradient. Small on purpose. */
    const val BODY_TOP_LIFT = 0.06f

    /** Backdrop darkening at the bottom of the body gradient. */
    const val BODY_BOTTOM_DEEPEN = 0.10f

    /** Where the lifted top has fully returned to the base fill. */
    const val BODY_MID_STOP = 0.55f

    /** Specular highlight at the very top edge. */
    const val SHEEN_TOP = 0.16f

    /** Specular highlight where the sheen has mostly faded. */
    const val SHEEN_MID = 0.05f

    /** How far down the panel the sheen survives (fraction of height). */
    const val SHEEN_EXTENT = 0.45f

    /**
     * Gradient hairline, as a multiplier of the edge token's own alpha (the
     * 10%-white [DhunColors.glassEdge]). The top keeps the token as-is; the
     * bottom fades to a third of it, so the edge catches light instead of
     * outlining the panel like a Material border.
     */
    const val EDGE_TOP_ALPHA = 1f
    const val EDGE_BOTTOM_ALPHA = 0.34f

    /**
     * Dialog/sheet body opacity floor. "Sufficiently opaque" (docs/TRD §,
     * design contract rule 1) is a *number*, not a licence to go flat: at this
     * alpha the frosted artwork and the page still read through the edges
     * while body text keeps the contrast the gates assert.
     */
    const val DIALOG_BODY_ALPHA_DARK = 0.91f
    const val DIALOG_BODY_ALPHA_LIGHT = 0.93f

    /** Body gradient stops for [base] — three stops of the same colour family. */
    fun bodyStops(base: Color, light: Boolean): List<Pair<Float, Color>> {
        val lift = if (light) BODY_TOP_LIFT * 0.6f else BODY_TOP_LIFT
        return listOf(
            0f to blend(base, Color.White, lift),
            BODY_MID_STOP to base,
            1f to blend(base, Color.Black, BODY_BOTTOM_DEEPEN),
        )
    }

    /** Specular white stops, fading to nothing at [SHEEN_EXTENT]. */
    fun sheenStops(light: Boolean): List<Pair<Float, Float>> {
        val scale = if (light) 0.45f else 1f
        return listOf(
            0f to SHEEN_TOP * scale,
            SHEEN_EXTENT * 0.55f to SHEEN_MID * scale,
            SHEEN_EXTENT to 0f,
        )
    }

    /** Gradient hairline stops for the glass edge, as alpha multipliers. */
    fun edgeStops(light: Boolean): List<Pair<Float, Float>> = listOf(
        0f to if (light) EDGE_TOP_ALPHA * 0.7f else EDGE_TOP_ALPHA,
        1f to EDGE_BOTTOM_ALPHA,
    )

    /** Dialog body fill: the readable tonal rung at the dialog opacity floor. */
    fun dialogBody(light: Boolean, tint: Color): Color =
        tint.copy(alpha = if (light) DIALOG_BODY_ALPHA_LIGHT else DIALOG_BODY_ALPHA_DARK)

    /** @return [base] moved [fraction] of the way to [target], alpha preserved. */
    fun blend(base: Color, target: Color, fraction: Float): Color {
        val f = fraction.coerceIn(0f, 1f)
        return Color(
            red = base.red + (target.red - base.red) * f,
            green = base.green + (target.green - base.green) * f,
            blue = base.blue + (target.blue - base.blue) * f,
            alpha = base.alpha,
        )
    }
}

/**
 * The now-playing cover, published once by the shell so a dialog or menu can
 * frost itself over the same artwork the page already shows — without every
 * call site threading a URL through three layers of parameters.
 *
 * Null when nothing is playing (or the track has no artwork): the frost cut
 * then paints its gradient and sheen over the dim scrim alone, which is the
 * documented fallback, not a broken surface.
 */
val LocalNowPlayingArtwork = staticCompositionLocalOf<String?> { null }

/** Convenience provider used by the shell (and by previews). */
@Composable
fun ProvideNowPlayingArtwork(artworkUrl: String?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNowPlayingArtwork provides artworkUrl, content = content)
}

/** @return the explicit URL when given, else the ambient now-playing cover. */
@Composable
internal fun resolveGlassArtwork(explicit: String?): String? =
    explicit?.takeIf { it.isNotBlank() }
        ?: LocalNowPlayingArtwork.current?.takeIf { it.isNotBlank() }

/**
 * Shared content material — **the** glass surface of the app.
 *
 * In-page cards use the reading fill as their base; dialogs use
 * `opaqueBase = true`, which raises the body to the dialog opacity floor and
 * turns on the frosted-artwork cut. Either way the panel is painted as glass:
 * frosted cover (frost cut), body gradient, specular sheen, gradient hairline
 * edge ([GlassMaterialPolicy]).
 *
 * Content stays sharp on top: the blur never sits on text. New UI should reach
 * for this (or [GlassDialog]) instead of a bare `background(DhunColors.surface)`
 * or a Material `Card`/`Surface`, which is what made screens read as default
 * Material 3.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = DhunShapes.glass,
    /** Kept for call-site compatibility; blur belongs behind the card. */
    blurRadius: Dp = DhunSpacing.glassBlur,
    contentPadding: PaddingValues? = null,
    elevated: Boolean = true,
    tint: Color = Color.Transparent,
    borderColor: Color = DhunColors.glassEdge,
    opaqueBase: Boolean = false,
    /** Use on an existing reading surface to avoid stacking two dark/light fills. */
    subtle: Boolean = false,
    /**
     * Raw thumbnail URL of the cover to frost this surface with. The cover is
     * blurred once per track and cached, then painted *under* the body — so the
     * panel is visibly made of the artwork the page shows instead of a flat
     * tint. Dialogs pass the playing track's cover; pages omit it because the
     * page backdrop is already that blur.
     */
    artworkUrl: String? = null,
    /**
     * Paint the frozen artwork under the body. Defaults to [opaqueBase], because
     * dialogs and sheets are the surfaces that float over page artwork; an
     * in-page card does not need a second blur of the same cover.
     */
    frostArtwork: Boolean = opaqueBase,
    /**
     * Which surface this panel's frost obeys. A dialog floats over whatever page
     * is behind it, so it defaults to that page; a card inside the player passes
     * `FULL_PLAYER` and one in the dock passes `MINI_PLAYER`.
     */
    component: BackgroundComponent? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val ignoredBlur = blurRadius
    val light = DhunAppearance.tokens.isLight
    val body = when {
        // Dialogs stay readable, but as *milky* glass: the tonal rung at the
        // documented opacity floor, not a flat opaque plate.
        opaqueBase -> GlassMaterialPolicy.dialogBody(light, DhunColors.surfaceVariant)
        subtle -> DhunColors.overlayFocus
        else -> readingSurfaceFill()
    }
    val resolvedComponent = component ?: LocalBackgroundComponent.current
    // No frost at all while Background blur is off: the panel is its body
    // gradient, sheen and edge, not a sharp copy of the cover under a wash.
    val frostUrl = if (frostArtwork && DhunBackgrounds.showsArtwork(resolvedComponent)) {
        resolveGlassArtwork(artworkUrl)
    } else {
        null
    }
    val bodyStops = GlassMaterialPolicy.bodyStops(body, light).toTypedArray()
    val sheen = sheenStops(light)
    val outer = if (elevated) {
        modifier.shadow(DhunSpacing.sm, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.18f))
    } else {
        modifier
    }

    Box(
        modifier = outer
            .clip(shape)
            .border(DhunSpacing.border, edgeBrush(light, borderColor), shape),
    ) {
        // 1. Frosted artwork under everything (frost cut only). Painted as a
        //    child — not a modifier background — so it sits *behind* the body.
        if (frostUrl != null) {
            LyricsArtworkLayer(
                artworkUrl = frostUrl,
                modifier = Modifier.matchParentSize(),
                component = resolvedComponent,
            )
        }
        // 2. Body: a gradient, never one flat colour.
        Box(
            modifier = Modifier.matchParentSize()
                .background(Brush.verticalGradient(*bodyStops), shape),
        )
        // 3. Optional caller tint, capped so it can only ever tint glass.
        if (tint.alpha > 0.01f) {
            Box(
                modifier = Modifier.matchParentSize()
                    .background(tint.copy(alpha = tint.alpha.coerceAtMost(0.20f)), shape),
            )
        }
        // 4. Specular sheen across the top.
        Box(
            modifier = Modifier.matchParentSize()
                .background(Brush.verticalGradient(*sheen), shape),
        )
        // 5. Content, sharp, at the caller's padding.
        if (contentPadding != null) {
            Box(modifier = Modifier.padding(contentPadding), content = content)
        } else {
            Box(content = content)
        }
    }
}

/** The specular sheen brush (white, fading down the panel). */
private fun sheenStops(light: Boolean): Array<Pair<Float, Color>> =
    GlassMaterialPolicy.sheenStops(light)
        .map { (offset, alpha) -> offset to Color.White.copy(alpha = alpha) }
        .toTypedArray()

/**
 * The glass body as a paint-only modifier, for chrome that is not a card: the
 * wide-layout navigation rail, which must be the same material as the dock and
 * the panels rather than one flat translucent strip.
 *
 * Same recipe as [GlassCard] steps 2 and 4 (body gradient, then sheen), so the
 * material is defined once (`docs/design/glass-system.md` §6).
 */
@Composable
fun Modifier.dhunGlassBody(fill: Color, shape: Shape = RectangleShape): Modifier {
    val light = DhunAppearance.tokens.isLight
    val bodyStops = GlassMaterialPolicy.bodyStops(fill, light).toTypedArray()
    val sheen = sheenStops(light)
    return this
        .background(Brush.verticalGradient(*bodyStops), shape)
        .background(Brush.verticalGradient(*sheen), shape)
}

/** The gradient hairline: the edge token, fading down the panel. */
private fun edgeBrush(light: Boolean, edge: Color): Brush {
    val tokenAlpha = edge.alpha.takeIf { it > 0f } ?: 0.22f
    val stops = GlassMaterialPolicy.edgeStops(light)
        .map { (offset, factor) -> offset to edge.copy(alpha = (tokenAlpha * factor).coerceIn(0f, 1f)) }
        .toTypedArray()
    return Brush.verticalGradient(*stops)
}

/**
 * The glass dialog: one frosted card, no Material-default chrome.
 *
 * Replaces raw `AlertDialog`/`BasicAlertDialog` call sites (the playback
 * diagnostics dialog was the last one) so a dialog cannot be the one flat,
 * outlined, default-Material surface in an otherwise glassy app.
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    artworkUrl: String? = null,
    component: BackgroundComponent? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedComponent = component ?: LocalBackgroundComponent.current
    Dialog(onDismissRequest = onDismissRequest) {
        GlassCard(
            modifier = modifier.widthIn(
                min = DhunSpacing.dialogMinWidth,
                max = DhunSpacing.dialogMaxWidth,
            ),
            shape = DhunShapes.large,
            opaqueBase = true,
            artworkUrl = artworkUrl ?: LocalNowPlayingArtwork.current,
            frostArtwork = true,
            component = resolvedComponent,
        ) {
            Column(modifier = Modifier.padding(DhunSpacing.lg), content = content)
        }
    }
}

/**
 * Continuous bottom dock: its own artwork layer under the mini-player and the
 * tab band, with the same gradient hairline as every panel.
 *
 * The bands veil themselves (the mini-player is acrylic, the tab band uses the
 * lyrics material), so the dock deliberately paints **no** body fill of its own
 * — one more fill here would double-darken the chrome. It still owns one blur
 * for the whole dock rather than one per band.
 */
@Composable
fun GlassDock(
    /** The **raw** current-track thumbnail URL; resolved/tiered by the policy. */
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    shape: Shape = DhunShapes.bottomSheet,
    component: BackgroundComponent = BackgroundComponent.MINI_PLAYER,
    content: @Composable ColumnScope.() -> Unit,
) {
    val light = DhunAppearance.tokens.isLight
    Box(
        modifier = modifier
            .shadow(DhunSpacing.md, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.28f))
            .clip(shape)
            .border(DhunSpacing.border, edgeBrush(light, DhunColors.glassEdge), shape),
    ) {
        // One blur for the whole dock. Each band veils itself so the
        // mini-player can be acrylic and the tabs below it can be the
        // lyrics material, instead of one near-black slab over both.
        LyricsArtworkLayer(
            artworkUrl = artworkUrl,
            modifier = Modifier.matchParentSize(),
            component = component,
        )
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/**
 * Compact chrome (chips, small pills): the accent when selected, otherwise a
 * glass body gradient over the chip fill with the shared hairline edge.
 *
 * A chip is too small for a frost cut — the artwork under it would be a smear,
 * not a readable surface — so it carries the body gradient and the edge only.
 */
@Composable
fun FrostedChipSurface(
    modifier: Modifier = Modifier,
    shape: Shape = DhunShapes.chip,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val light = DhunAppearance.tokens.isLight
    val fill = if (selected) DhunColors.accent else contentRowFill(VisualPart.CHIPS)
    val bodyStops = GlassMaterialPolicy.bodyStops(fill, light).toTypedArray()
    Box(
        modifier = modifier
            .clip(shape)
            // Selected chips are the accent (flat on purpose: a gradient under
            // the label would fight it); unselected ones carry the glass body.
            .then(
                if (selected) {
                    Modifier.background(fill, shape)
                } else {
                    Modifier.background(Brush.verticalGradient(*bodyStops), shape)
                },
            )
            .border(
                DhunSpacing.border,
                if (selected) DhunColors.accent.copy(alpha = 0.5f) else DhunColors.glassEdge,
                shape,
            ),
        content = content,
    )
}
