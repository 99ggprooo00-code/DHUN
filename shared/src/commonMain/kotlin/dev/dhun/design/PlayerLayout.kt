package dev.dhun.design

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize

/**
 * Full-player layout modes. Portrait keeps the familiar artwork → metadata →
 * controls hierarchy; a truly wide viewport puts the same control cluster
 * beside the hero so a landscape phone or desktop does not collapse the cover
 * into the few pixels left above a tall bottom stack.
 */
internal enum class FullPlayerLayoutMode { Stacked, Wide }

/**
 * Chooses the wide composition only when there is enough horizontal room for
 * both a usable artwork field and the control column. Invalid/unmeasured
 * constraints deliberately fall back to the safer stacked layout.
 */
internal fun fullPlayerLayoutMode(availableWidth: Dp, availableHeight: Dp): FullPlayerLayoutMode {
    val width = availableWidth.value
    val height = availableHeight.value
    return if (
        width.isFinite() && height.isFinite() &&
        availableWidth > DhunSpacing.zero && availableHeight > DhunSpacing.zero &&
        availableWidth >= DhunSpacing.playerWideLayoutMinWidth &&
        availableWidth > availableHeight
    ) {
        FullPlayerLayoutMode.Wide
    } else {
        FullPlayerLayoutMode.Stacked
    }
}

/**
 * The width allocated to the wide layout's controls. It grows with the window
 * until the existing comfortable transport measure, leaving all other width
 * to the artwork field. The final clamp protects a transient zero-width
 * measurement during resize.
 */
internal fun playerWideControlsWidth(availableWidth: Dp): Dp {
    val width = finiteNonNegativePlayerDimension(availableWidth)
    val desired = width * PLAYER_WIDE_CONTROLS_WIDTH_FRACTION
    return desired
        .coerceIn(DhunSpacing.playerWideControlsMinWidth, DhunSpacing.playerWideControlsMaxWidth)
        .coerceAtMost((width - DhunSpacing.playerWideLayoutGap).coerceAtLeast(DhunSpacing.zero))
}

/** Tighter gaps only; all interactive controls retain their existing target sizes. */
internal fun usesCompactPlayerControls(availableHeight: Dp): Boolean =
    availableHeight.value.isFinite() &&
        availableHeight > DhunSpacing.zero &&
        availableHeight <= DhunSpacing.playerCompactControlsHeight

/**
 * Both axes constrain the hero. Callers hand this the *padded hero field*,
 * not a guessed device size, so the cover naturally expands into the room
 * between the header and the persistent metadata/control cluster. The width
 * ceiling prevents absurd artwork on desktop while keeping tablets and
 * desktop windows immersive.
 *
 * Aspect-aware (2026-11 immersive artwork pass): the old square card cropped
 * every non-square cover and left the "framed thumbnail" look on square ones.
 * The box now fills the field's **width** (up to [DhunSpacing.playerArtworkMaxSize])
 * and derives its height from the artwork's own measured aspect ratio, capped
 * by the field height — i.e. the largest aspect-correct rectangle the field
 * admits. Square art on a phone therefore spans the whole stage width, a 16:9
 * cover becomes a full-width band, and a tall cover centres at full height;
 * nothing is ever stretched, and nothing is cropped by the layout.
 *
 * [aspectRatio] is width/height as measured from the decoded image (1:1 until
 * the first frame arrives). Non-finite or non-positive values fall back to
 * square rather than producing a NaN box.
 */
internal fun fittedPlayerArtworkBox(availableWidth: Dp, availableHeight: Dp, aspectRatio: Float): DpSize {
    val fieldWidth = finiteNonNegativePlayerDimension(availableWidth).coerceAtMost(DhunSpacing.playerArtworkMaxSize)
    val fieldHeight = finiteNonNegativePlayerDimension(availableHeight)
    val aspect = if (aspectRatio.isFinite() && aspectRatio > 0f) aspectRatio else 1f
    val width = minOf(fieldWidth, fieldHeight * aspect)
    return DpSize(width, width / aspect)
}

private fun finiteNonNegativePlayerDimension(value: Dp): Dp =
    if (value.value.isFinite()) value.coerceAtLeast(DhunSpacing.zero) else DhunSpacing.zero

private const val PLAYER_WIDE_CONTROLS_WIDTH_FRACTION = 0.34f
