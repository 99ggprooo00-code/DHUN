package dev.dhun.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing

/**
 * Consequence-first confirmation surface for destructive or consequential
 * mutations. Callers still own their copy, mutation state, and callbacks; this
 * component only gives the user a clearer consequence / recovery hierarchy.
 *
 * [onDismissRequest] is ignored while [isPending], so Back, outside tap, and
 * Cancel preserve the result-driven mutation behavior.
 */
@Composable
fun DhunConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "Cancel",
    retryLabel: String = "Try again",
    pendingLabel: String = "Working…",
    isPending: Boolean = false,
    errorMessage: String? = null,
) {
    GlassDialog(
        onDismissRequest = { if (!isPending) onDismissRequest() },
        modifier = modifier.heightIn(max = DhunSpacing.dialogBodyMaxHeight),
        maxWidth = DhunSpacing.dialogWideMaxWidth,
        contentPadding = PaddingValues(
            horizontal = DhunSpacing.dialogContentHorizontalPadding,
            vertical = DhunSpacing.dialogContentVerticalPadding,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .heightIn(max = DhunSpacing.dialogBodyMaxHeight)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier.size(DhunSpacing.dialogIconBadgeSize)
                        .clip(CircleShape)
                        .background(DhunColors.errorContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    DhunIconView(
                        icon = DhunIcon.Error,
                        contentDescription = null,
                        modifier = Modifier.size(DhunSpacing.iconSizeSm),
                        tint = DhunColors.error,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
                ) {
                    Text(
                        text = "Confirm action",
                        style = MaterialTheme.typography.labelMedium,
                        color = DhunColors.error,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = DhunColors.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
            Text(
                text = message,
                modifier = Modifier.padding(top = DhunSpacing.md),
                style = MaterialTheme.typography.bodyMedium,
                color = DhunColors.textSecondary,
            )
            if (errorMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = DhunSpacing.sm)
                        .clip(DhunShapes.medium)
                        .background(DhunColors.errorContainer)
                        .padding(horizontal = DhunSpacing.md, vertical = DhunSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DhunIconView(
                        icon = DhunIcon.Error,
                        contentDescription = null,
                        modifier = Modifier.size(DhunSpacing.iconSizeSm),
                        tint = DhunColors.error,
                    )
                    Text(
                        text = errorMessage,
                        modifier = Modifier.weight(1f).semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = DhunColors.error,
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = DhunSpacing.lg),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
            ) {
                DhunTextButton(onClick = onDismissRequest, enabled = !isPending) {
                    Text(dismissLabel, maxLines = 1)
                }
                DhunDestructiveButton(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isPending,
                    contentPadding = PaddingValues(
                        horizontal = DhunSpacing.dialogActionHorizontalPadding,
                        vertical = DhunSpacing.dialogActionVerticalPadding,
                    ),
                ) {
                    if (isPending) {
                        DhunSpinner(
                            modifier = Modifier.size(DhunSpacing.iconSizeSm),
                            strokeWidth = DhunSpacing.iconStroke,
                            color = DhunColors.textDisabled,
                        )
                        Spacer(modifier = Modifier.width(DhunSpacing.xs))
                        Text(pendingLabel, maxLines = 1)
                    } else {
                        Text(if (errorMessage != null) retryLabel else confirmLabel, maxLines = 1)
                    }
                }
            }
        }
    }
}
