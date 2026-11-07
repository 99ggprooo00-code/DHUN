package dev.dhun.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunSpacing

/**
 * Compact, theme-safe confirmation pattern for destructive or consequential
 * actions. Callers own their mutation state and callbacks; this component owns
 * only the shared glass surface, type hierarchy, spacing, and action alignment.
 *
 * [onDismissRequest] is ignored while [isPending], so Back, outside tap, and
 * Cancel all preserve the existing result-driven mutation behavior.
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
        modifier = modifier,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = DhunColors.textPrimary,
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = DhunSpacing.sm),
            style = MaterialTheme.typography.bodyMedium,
            color = DhunColors.textSecondary,
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                modifier = Modifier.padding(top = DhunSpacing.sm).semantics {
                    liveRegion = LiveRegionMode.Polite
                },
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.error,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = DhunSpacing.md),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DhunTextButton(onClick = onDismissRequest, enabled = !isPending) {
                Text(dismissLabel, maxLines = 1)
            }
            Spacer(modifier = Modifier.width(DhunSpacing.sm))
            DhunButton(
                onClick = onConfirm,
                enabled = !isPending,
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
