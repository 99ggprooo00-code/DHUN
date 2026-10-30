package dev.dhun.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.GlassCard

/**
 * Screen-level failure notice for a mutation that has no dialog of its own —
 * a Like that did not save, a history row that could not be removed, a
 * download that could not be deleted.
 *
 * It is a **glass panel with an error tint** ([GlassCard] with `tint =`
 * errorContainer), not a flat Material `Surface` strip: the app's one surface
 * material stays in charge of shape, edge, sheen and depth, and the error is
 * carried by the tint plus the error-coloured text. It is a polite live region,
 * so TalkBack announces it instead of leaving a silent no-op.
 *
 * The control that failed stays on screen, which is the retry: tapping it again
 * re-runs the mutation and clears the message when it succeeds.
 */
@Composable
fun ActionErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        tint = DhunColors.errorContainer,
        elevated = false,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = DhunSpacing.md, end = DhunSpacing.sm, top = DhunSpacing.xs, bottom = DhunSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.error,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            DhunTextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
