package dev.dhun.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing

/**
 * DHUN's switch and slider colours.
 *
 * Material components are fine as *primitives* — the design contract says so —
 * but their default palette is not DHUN's. A bare `Switch()` / `Slider()` picks
 * Material's own track/thumb/border defaults, which is exactly how a screen
 * full of DHUN glass ends up with two controls that read as stock Material 3.
 *
 * These are pure functions so the palette can be asserted without a rendered
 * frame; [DhunSwitch] and [DhunSlider] are the only widgets that should be used.
 */
object DhunControlPolicy {

    /** Off-track is glass, not a Material grey groove. */
    fun switchTrackOff(): Color = DhunColors.glassStrong

    /** Off-thumb reads as frosted chrome on top of that glass. */
    fun switchThumbOff(): Color = DhunColors.textSecondary

    /** Off-border is the same hairline every glass panel uses. */
    fun switchBorderOff(): Color = DhunColors.glassEdge

    /** On-track and on-border are the accent — unmistakable without colour alone (thumb fill). */
    fun switchTrackOn(): Color = DhunColors.accent

    fun switchBorderOn(): Color = DhunColors.accent

    /** The knob on top of the accent track stays high-contrast. */
    fun switchThumbOn(): Color = DhunColors.onAccent

    fun sliderThumb(): Color = DhunColors.accent

    fun sliderActiveTrack(): Color = DhunColors.accent

    /** The unfilled groove is translucent glass, so the surface reads through it. */
    fun sliderInactiveTrack(): Color = DhunColors.glassStrong

    /** Unchecked box is glass with a hairline edge, not a grey Material outline. */
    fun checkboxFillOff(): Color = DhunColors.glassStrong

    fun checkboxBorderOff(): Color = DhunColors.glassEdge

    /** Checked box is the accent, with the tick in on-accent so it never disappears. */
    fun checkboxFillOn(): Color = DhunColors.accent

    fun checkboxBorderOn(): Color = DhunColors.accent

    fun checkboxGlyphOn(): Color = DhunColors.onAccent

    /** Progress fill is the accent; the groove behind it is translucent glass. */
    fun progressFill(): Color = DhunColors.accent

    fun progressTrack(): Color = DhunColors.glassStrong

    fun disabled(color: Color): Color = color.copy(alpha = DISABLED_ALPHA)

    /** Applies [disabled] only when the control is not interactive. */
    fun disableIf(enabled: Boolean, color: Color): Color =
        if (enabled) color else disabled(color)

    /** The one disabled opacity for every DHUN control. */
    const val DISABLED_ALPHA = 0.38f
}

/** A DHUN-styled switch. Use instead of a bare Material `Switch`. */
@Composable
fun DhunSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = SwitchDefaults.colors(
        checkedThumbColor = DhunControlPolicy.switchThumbOn(),
        checkedTrackColor = DhunControlPolicy.switchTrackOn(),
        checkedBorderColor = DhunControlPolicy.switchBorderOn(),
        checkedIconColor = DhunControlPolicy.switchThumbOn(),
        uncheckedThumbColor = DhunControlPolicy.switchThumbOff(),
        uncheckedTrackColor = DhunControlPolicy.switchTrackOff(),
        uncheckedBorderColor = DhunControlPolicy.switchBorderOff(),
        uncheckedIconColor = DhunControlPolicy.switchThumbOff(),
        disabledCheckedThumbColor = DhunControlPolicy.disabled(DhunControlPolicy.switchThumbOn()),
        disabledCheckedTrackColor = DhunControlPolicy.disabled(DhunControlPolicy.switchTrackOn()),
        disabledCheckedBorderColor = DhunControlPolicy.disabled(DhunControlPolicy.switchBorderOn()),
        disabledCheckedIconColor = DhunControlPolicy.disabled(DhunControlPolicy.switchThumbOn()),
        disabledUncheckedThumbColor = DhunControlPolicy.disabled(DhunControlPolicy.switchThumbOff()),
        disabledUncheckedTrackColor = DhunControlPolicy.disabled(DhunControlPolicy.switchTrackOff()),
        disabledUncheckedBorderColor = DhunControlPolicy.disabled(DhunControlPolicy.switchBorderOff()),
        disabledUncheckedIconColor = DhunControlPolicy.disabled(DhunControlPolicy.switchThumbOff()),
    )
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
    )
}

/** A DHUN-styled slider: accent-filled groove over translucent glass. */
@Composable
fun DhunSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = SliderDefaults.colors(
        thumbColor = DhunControlPolicy.sliderThumb(),
        activeTrackColor = DhunControlPolicy.sliderActiveTrack(),
        inactiveTrackColor = DhunControlPolicy.sliderInactiveTrack(),
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
        disabledThumbColor = DhunControlPolicy.disabled(DhunControlPolicy.sliderThumb()),
        disabledActiveTrackColor = DhunControlPolicy.disabled(DhunControlPolicy.sliderActiveTrack()),
        disabledInactiveTrackColor = DhunControlPolicy.disabled(DhunControlPolicy.sliderInactiveTrack()),
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
    )
}

/**
 * A DHUN checkbox for multi-select rows (offline download selection).
 *
 * Material's `Checkbox` renders its own square outline and ripple-free tick with
 * Material's palette; inside DHUN it reads as a stock control in the middle of a
 * glass list. This one is a translucent glass square with the same hairline edge
 * as every panel, filled with the accent when checked, and it keeps the 48dp
 * touch target Material gives you (the tap area is larger than the glyph).
 */
@Composable
fun DhunCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val fill = DhunControlPolicy.disableIf(
        enabled,
        if (checked) DhunControlPolicy.checkboxFillOn() else DhunControlPolicy.checkboxFillOff(),
    )
    val edge = DhunControlPolicy.disableIf(
        enabled,
        if (checked) DhunControlPolicy.checkboxBorderOn() else DhunControlPolicy.checkboxBorderOff(),
    )
    val glyph = DhunControlPolicy.disableIf(enabled, DhunControlPolicy.checkboxGlyphOn())
    Box(
        modifier = modifier
            .size(DhunSpacing.touchTarget)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(DhunShapes.extraSmall)
                .background(fill)
                .border(DhunSpacing.border, edge, DhunShapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                DhunIconView(
                    icon = DhunIcon.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = glyph,
                )
            }
        }
    }
}

/**
 * A DHUN progress bar (download progress, device storage).
 *
 * Material's `LinearProgressIndicator` is fine as a primitive, but its default
 * track is an opaque Material surface and its default height/caps are Material
 * metrics: at 3-6dp inside a glass row that reads as a stock Material bar. This
 * pins the pill shape, the glass groove and the accent fill in one place.
 *
 * @param progress `0f..1f` for a determinate bar, or `null` for an
 *   indeterminate one (the queue/player already own their own progress UI).
 */
@Composable
fun DhunProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    trackHeight: Dp = DhunSpacing.progressStroke,
) {
    val bar = modifier
        .fillMaxWidth()
        .height(trackHeight)
        .clip(DhunShapes.full)
    if (progress == null) {
        LinearProgressIndicator(
            modifier = bar,
            color = DhunControlPolicy.progressFill(),
            trackColor = DhunControlPolicy.progressTrack(),
        )
    } else {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = bar,
            color = DhunControlPolicy.progressFill(),
            trackColor = DhunControlPolicy.progressTrack(),
        )
    }
}
