package dev.dhun.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import dev.dhun.core.PlaybackState
import dev.dhun.core.diagnosticText
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.ArtworkImage
import dev.dhun.design.components.DhunButton
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.GlassDialog

/** Same recovery surface from either player; technical diagnostics stay complete and selectable. */
@Composable
internal fun PlaybackErrorDialog(error: PlaybackState.Error, onDismiss: () -> Unit, onRetry: () -> Unit) {
    var showDiagnostics by remember(error) { mutableStateOf(false) }
    val details = error.detail?.takeIf { it.isNotBlank() }
    val hasDiagnostics = error.track != null || details != null

    GlassDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().heightIn(max = DhunSpacing.dialogBodyMaxHeight),
        maxWidth = DhunSpacing.dialogWideMaxWidth,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = DhunSpacing.dialogContentHorizontalPadding,
            vertical = DhunSpacing.dialogContentVerticalPadding,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = DhunSpacing.dialogBodyMaxHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
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
                Column(verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs)) {
                    Text(
                        "Playback needs attention",
                        style = MaterialTheme.typography.labelMedium,
                        color = DhunColors.error,
                    )
                    Text(
                        "Playback details",
                        style = MaterialTheme.typography.titleMedium,
                        color = DhunColors.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(DhunShapes.medium)
                    .background(DhunColors.errorContainer)
                    .padding(horizontal = DhunSpacing.md, vertical = DhunSpacing.smPlus),
                horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                DhunIconView(
                    icon = DhunIcon.Error,
                    contentDescription = null,
                    modifier = Modifier.padding(top = DhunSpacing.iconStroke).size(DhunSpacing.iconSizeSm),
                    tint = DhunColors.error,
                )
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        error.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DhunColors.textPrimary,
                    )
                }
            }

            error.track?.let { track ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(DhunShapes.medium)
                        .background(DhunColors.surfaceContainerLow)
                        .padding(DhunSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkImage(
                        imageUrl = track.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.size(DhunSpacing.menuArtwork),
                        shape = DhunShapes.small,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = DhunColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            track.artistName,
                            style = MaterialTheme.typography.labelSmall,
                            color = DhunColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (hasDiagnostics) {
                DhunTextButton(
                    onClick = { showDiagnostics = !showDiagnostics },
                    modifier = Modifier.fillMaxWidth().semantics {
                        stateDescription = if (showDiagnostics) "Expanded" else "Collapsed"
                    },
                ) {
                    Text(if (showDiagnostics) "Hide diagnostics" else "Show diagnostics")
                    Spacer(modifier = Modifier.width(DhunSpacing.xs))
                    DhunIconView(
                        icon = DhunIcon.ChevronDown,
                        contentDescription = null,
                        modifier = Modifier.size(DhunSpacing.iconSizeSm).graphicsLayer {
                            rotationZ = if (showDiagnostics) 180f else 0f
                        },
                        tint = DhunColors.accent,
                    )
                }
                if (showDiagnostics) {
                    SelectionContainer {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .heightIn(max = DhunSpacing.playerDiagnosticsMaxHeight)
                                .clip(DhunShapes.medium)
                                .background(DhunColors.surfaceContainerLow)
                                .verticalScroll(rememberScrollState())
                                .padding(DhunSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
                        ) {
                            error.track?.let {
                                Text(
                                    "Track ID: ${it.id}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DhunColors.textSecondary,
                                )
                            }
                            details?.let {
                                Text(
                                    diagnosticText(it, 4_000),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DhunColors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = DhunSpacing.sm),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DhunTextButton(onClick = onDismiss) {
                    Text("Close", color = DhunColors.textSecondary)
                }
                Spacer(modifier = Modifier.width(DhunSpacing.sm))
                DhunButton(onClick = { onDismiss(); onRetry() }) {
                    Text("Retry")
                }
            }
        }
    }
}
