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
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing

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
            containerColor = DhunColors.surfaceElevated,
            labelColor = DhunColors.textSecondary,
            iconColor = DhunColors.textSecondary,
            selectedContainerColor = DhunColors.accent,
            selectedLabelColor = DhunColors.onAccent,
            selectedLeadingIconColor = DhunColors.onAccent,
            disabledContainerColor = DhunColors.surface,
            disabledLabelColor = DhunColors.textDisabled,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
        ),
    )
}

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
            containerColor = DhunColors.surfaceElevated,
            labelColor = DhunColors.textSecondary,
        ),
        border = BorderStroke(DhunSpacing.border, DhunColors.border),
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
            containerColor = DhunColors.surfaceElevated,
            labelColor = DhunColors.textSecondary,
            selectedContainerColor = DhunColors.accentContainer,
            selectedLabelColor = DhunColors.onAccentContainer,
        ),
        border = InputChipDefaults.inputChipBorder(
            enabled = true,
            selected = selected,
        ),
    )
}
