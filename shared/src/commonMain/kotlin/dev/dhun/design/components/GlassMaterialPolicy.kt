package dev.dhun.design.components

import androidx.compose.ui.graphics.Color

/**
 * Numeric contract for DHUN's translucent glass material.
 *
 * A panel is built from a readable body gradient, a restrained specular sheen
 * and a directional hairline edge. Blur belongs to the artwork behind the
 * panel, never to its text. Keeping the recipe here lets cards, dialogs and
 * floating chrome share one identity without making every list row expensive.
 *
 * Dark and light values are deliberate peers: light mode weakens white sheen
 * and elevation instead of reusing or inverting the dark treatment. Gradient
 * stops preserve their input alpha; content/dialog opacity floors are explicit
 * policy decisions rather than accidental double tinting.
 */
object GlassMaterialPolicy {
    /** White added to the top of the dark body gradient. */
    const val BODY_TOP_LIFT = 0.06f

    /** Black added to the bottom of both body gradients. */
    const val BODY_BOTTOM_DEEPEN = 0.10f

    /** Where the lifted top has returned to the unmodified body fill. */
    const val BODY_MID_STOP = 0.55f

    /** Maximum specular highlight; light mode applies its own smaller scale. */
    const val SHEEN_TOP = 0.16f
    const val SHEEN_MID = 0.05f
    const val SHEEN_EXTENT = 0.45f
    const val SHEEN_LIGHT_SCALE = 0.45f

    /** Directional edge multipliers, applied to the active theme's edge token. */
    const val EDGE_TOP_ALPHA = 1f
    const val EDGE_TOP_LIGHT_SCALE = 0.70f
    const val EDGE_BOTTOM_ALPHA = 0.34f

    /** High-opacity dialog glass: readable, but not an opaque plate. */
    const val DIALOG_BODY_ALPHA_DARK = 0.91f
    const val DIALOG_BODY_ALPHA_LIGHT = 0.93f

    /**
     * Ordinary sheened cards need a stronger body and quieter dark sheen than
     * dialogs: their text sits directly over the page artwork. These bounds
     * come from the same black/white-artwork contrast gate as the dialog fill.
     */
    const val CONTENT_BODY_MIN_ALPHA = 0.70f
    const val CONTENT_SHEEN_TOP_DARK = 0.05f

    /** Elevation is quieter on paper-like light surfaces than on dark glass. */
    const val CARD_SHADOW_ALPHA_DARK = 0.18f
    const val CARD_SHADOW_ALPHA_LIGHT = 0.10f
    const val DOCK_SHADOW_ALPHA_DARK = 0.28f
    const val DOCK_SHADOW_ALPHA_LIGHT = 0.16f

    /** Three stops of one colour family; the readable fill is the middle stop. */
    fun bodyStops(base: Color, light: Boolean): List<Pair<Float, Color>> {
        val topLift = if (light) BODY_TOP_LIFT * 0.60f else BODY_TOP_LIFT
        return listOf(
            0f to blend(base, Color.White, topLift),
            BODY_MID_STOP to base,
            1f to blend(base, Color.Black, BODY_BOTTOM_DEEPEN),
        )
    }

    /** A top-edge highlight which fades completely before halfway down a panel. */
    fun sheenStops(light: Boolean): List<Pair<Float, Float>> {
        val scale = if (light) SHEEN_LIGHT_SCALE else 1f
        return listOf(
            0f to SHEEN_TOP * scale,
            SHEEN_EXTENT * 0.55f to SHEEN_MID * scale,
            SHEEN_EXTENT to 0f,
        )
    }

    /** Quieter sheen for ordinary content cards; dialog glass uses [sheenStops]. */
    fun contentSheenStops(light: Boolean): List<Pair<Float, Float>> {
        val stops = sheenStops(light)
        if (light) return stops
        val scale = CONTENT_SHEEN_TOP_DARK / SHEEN_TOP
        return stops.map { (offset, alpha) -> offset to alpha * scale }
    }

    /** Top-lit hairline multipliers for the active theme's own edge colour. */
    fun edgeStops(light: Boolean): List<Pair<Float, Float>> = listOf(
        0f to if (light) EDGE_TOP_ALPHA * EDGE_TOP_LIGHT_SCALE else EDGE_TOP_ALPHA,
        1f to EDGE_BOTTOM_ALPHA,
    )

    /** Raise only a sheened content card when its input fill is too sheer. */
    fun contentBody(fill: Color): Color =
        if (fill.alpha >= CONTENT_BODY_MIN_ALPHA) fill else fill.copy(alpha = CONTENT_BODY_MIN_ALPHA)

    /** The shared dialog fill at the mode-specific legibility floor. */
    fun dialogBody(light: Boolean, tint: Color): Color =
        tint.copy(alpha = if (light) DIALOG_BODY_ALPHA_LIGHT else DIALOG_BODY_ALPHA_DARK)

    fun cardShadowColor(light: Boolean): Color = Color.Black.copy(
        alpha = if (light) CARD_SHADOW_ALPHA_LIGHT else CARD_SHADOW_ALPHA_DARK,
    )

    fun dockShadowColor(light: Boolean): Color = Color.Black.copy(
        alpha = if (light) DOCK_SHADOW_ALPHA_LIGHT else DOCK_SHADOW_ALPHA_DARK,
    )

    /**
     * Blur is an optional artwork effect, not a requirement for the material.
     * Unsupported platforms and a zero user preference both keep the dimmed
     * artwork fallback without installing a no-op RenderEffect.
     */
    fun shouldBlurArtwork(supportsRealtimeBlur: Boolean, blurPercent: Int): Boolean =
        supportsRealtimeBlur && blurPercent > 0

    /** Move [base] towards [target], preserving the caller's alpha exactly. */
    fun blend(base: Color, target: Color, fraction: Float): Color {
        val amount = fraction.coerceIn(0f, 1f)
        return Color(
            red = base.red + (target.red - base.red) * amount,
            green = base.green + (target.green - base.green) * amount,
            blue = base.blue + (target.blue - base.blue) * amount,
            alpha = base.alpha,
        )
    }
}
