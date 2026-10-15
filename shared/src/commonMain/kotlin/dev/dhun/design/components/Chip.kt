package dev.dhun.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.VisualPart

/**
 * Pill filter chip — quiet neutral unselected, solid accent selected.
 * M3 shape scale (`DhunShapes.chip` = full pill).
 */
@Composable
fun DhunFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        shape = DhunShapes.chip,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = contentRowFill(VisualPart.CHIPS),
            labelColor = DhunColors.textSecondary,
            iconColor = DhunColors.textSecondary,
            selectedContainerColor = DhunColors.accent,
            selectedLabelColor = DhunColors.onAccent,
            selectedLeadingIconColor = DhunColors.onAccent,
            disabledContainerColor = DhunColors.surface.copy(alpha = 0.5f),
            disabledLabelColor = DhunColors.textDisabled,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = DhunColors.glassEdge,
            selectedBorderColor = DhunColors.accent.copy(alpha = 0.45f),
        ),
    )
}

/** Assist / quick-action chip — same neutral body as the filter chips. */
@Composable
fun DhunAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    AssistChip(
        onClick = onClick,
        label = label,
        modifier = modifier,
        leadingIcon = leadingIcon,
        shape = DhunShapes.chip,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = contentRowFill(VisualPart.CHIPS),
            labelColor = DhunColors.textPrimary,
            leadingIconContentColor = DhunColors.accent,
        ),
        border = BorderStroke(DhunSpacing.border, DhunColors.glassEdge),
    )
}

@Composable
fun DhunInputChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    InputChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        shape = DhunShapes.chip,
        colors = InputChipDefaults.inputChipColors(
            containerColor = contentRowFill(VisualPart.CHIPS),
            labelColor = DhunColors.textSecondary,
            selectedContainerColor = DhunColors.accentContainer.copy(alpha = 0.9f),
            selectedLabelColor = DhunColors.onAccentContainer,
        ),
        border = InputChipDefaults.inputChipBorder(
            enabled = true,
            selected = selected,
            borderColor = DhunColors.glassEdge,
            selectedBorderColor = Color.Transparent,
        ),
    )
}
