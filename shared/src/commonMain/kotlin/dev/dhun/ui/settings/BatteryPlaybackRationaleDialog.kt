package dev.dhun.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.DhunButton
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.GlassDialog

/** DHUN-owned explanation shown before opening Android's battery-optimization settings. */
@Composable
fun BatteryPlaybackRationaleDialog(
    onDismiss: () -> Unit,
    onAllow: () -> Unit,
) {
    GlassDialog(
        onDismissRequest = onDismiss,
        maxWidth = DhunSpacing.dialogWideMaxWidth,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = DhunSpacing.dialogContentHorizontalPadding,
            vertical = DhunSpacing.dialogContentVerticalPadding,
        ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(DhunSpacing.dialogIconBadgeSize)
                    .clip(CircleShape)
                    .background(DhunColors.accentContainer),
                contentAlignment = Alignment.Center,
            ) {
                DhunIconView(
                    icon = DhunIcon.Music,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSizeSm),
                    tint = DhunColors.accent,
                )
            }
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs)) {
                Text(
                    "Background playback",
                    style = MaterialTheme.typography.labelMedium,
                    color = DhunColors.accent,
                )
                Text(
                    "Keep playback reliable",
                    style = MaterialTheme.typography.titleMedium,
                    color = DhunColors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        Text(
            "Android battery optimization can stop background music on some phones. Allow DHUN to keep the playback service available when the screen is off?",
            modifier = Modifier.fillMaxWidth().padding(top = DhunSpacing.md),
            style = MaterialTheme.typography.bodyMedium,
            color = DhunColors.textSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = DhunSpacing.lg),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DhunTextButton(onClick = onDismiss) { Text("Not now") }
            Spacer(modifier = Modifier.width(DhunSpacing.sm))
            DhunButton(onClick = onAllow) { Text("Allow") }
        }
    }
}
