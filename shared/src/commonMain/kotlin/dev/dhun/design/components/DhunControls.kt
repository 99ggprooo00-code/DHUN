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

/** Shared palette and disabled-state rules for small DHUN controls. */
object DhunControlPolicy {
    const val DISABLED_ALPHA = 0.38f

    fun checkboxFillOff(): Color = DhunColors.glassStrong
    fun checkboxBorderOff(): Color = DhunColors.glassEdge
    fun checkboxFillOn(): Color = DhunColors.accent
    fun checkboxBorderOn(): Color = DhunColors.accent
    fun checkboxGlyphOn(): Color = DhunColors.onAccent

    fun progressFill(): Color = DhunColors.accent
    fun progressTrack(): Color = DhunColors.glassStrong

    fun switchThumbOn(): Color = DhunColors.onAccent
    fun switchTrackOn(): Color = DhunColors.accent
    fun switchThumbOff(): Color = DhunColors.textSecondary
    fun switchTrackOff(): Color = DhunColors.glassStrong
    fun switchBorderOff(): Color = DhunColors.glassEdge

    fun sliderInactiveTrack(): Color = DhunColors.glassStrong
    fun sliderActiveTrack(accent: Color): Color = accent.copy(alpha = accent.alpha * 0.85f)

    fun progressValue(progress: Float?): Float? = progress?.let {
        if (it.isNaN()) 0f else it.coerceIn(0f, 1f)
    }

    fun disabled(color: Color): Color = color.copy(alpha = color.alpha * DISABLED_ALPHA)
    fun disableIf(enabled: Boolean, color: Color): Color = if (enabled) color else disabled(color)
}

/** DHUN switch colors with the same enabled/disabled contract as other controls. */
@Composable
fun DhunSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = DhunControlPolicy.switchThumbOn(),
            checkedTrackColor = DhunControlPolicy.switchTrackOn(),
            checkedBorderColor = DhunControlPolicy.switchTrackOn(),
            checkedIconColor = DhunControlPolicy.switchThumbOn(),
            uncheckedThumbColor = DhunControlPolicy.switchThumbOff(),
            uncheckedTrackColor = DhunControlPolicy.switchTrackOff(),
            uncheckedBorderColor = DhunControlPolicy.switchBorderOff(),
            uncheckedIconColor = DhunControlPolicy.switchThumbOff(),
            disabledCheckedThumbColor = DhunControlPolicy.disabled(DhunControlPolicy.switchThumbOn()),
            disabledCheckedTrackColor = DhunControlPolicy.disabled(DhunControlPolicy.switchTrackOn()),
            disabledCheckedBorderColor = DhunControlPolicy.disabled(DhunControlPolicy.switchTrackOn()),
            disabledCheckedIconColor = DhunControlPolicy.disabled(DhunControlPolicy.switchThumbOn()),
            disabledUncheckedThumbColor = DhunControlPolicy.disabled(DhunControlPolicy.switchThumbOff()),
            disabledUncheckedTrackColor = DhunControlPolicy.disabled(DhunControlPolicy.switchTrackOff()),
            disabledUncheckedBorderColor = DhunControlPolicy.disabled(DhunControlPolicy.switchBorderOff()),
            disabledUncheckedIconColor = DhunControlPolicy.disabled(DhunControlPolicy.switchThumbOff()),
        ),
    )
}

/** Shared slider mechanics with caller-preservable accent/track colors. */
@Composable
fun DhunSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    accent: Color = DhunColors.accent,
    inactiveTrackColor: Color = DhunControlPolicy.sliderInactiveTrack(),
) {
    val activeTrackColor = DhunControlPolicy.sliderActiveTrack(accent)
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        colors = SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = activeTrackColor,
            inactiveTrackColor = inactiveTrackColor,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
            disabledThumbColor = DhunControlPolicy.disabled(accent),
            disabledActiveTrackColor = DhunControlPolicy.disabled(activeTrackColor),
            disabledInactiveTrackColor = DhunControlPolicy.disabled(inactiveTrackColor),
        ),
    )
}

/** A 48dp checkbox target with a compact glass/accent glyph, not a stock box. */
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
 * Accent progress in a translucent glass groove. A null [progress] selects the
 * indeterminate primitive; determinate values are clamped to 0..1.
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
    val value = DhunControlPolicy.progressValue(progress)
    if (value == null) {
        LinearProgressIndicator(
            modifier = bar,
            color = DhunControlPolicy.progressFill(),
            trackColor = DhunControlPolicy.progressTrack(),
        )
    } else {
        LinearProgressIndicator(
            progress = { value },
            modifier = bar,
            color = DhunControlPolicy.progressFill(),
            trackColor = DhunControlPolicy.progressTrack(),
        )
    }
}
