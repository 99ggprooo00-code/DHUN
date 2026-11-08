package dev.dhun.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.dhun.core.Lyrics
import dev.dhun.core.Track
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.DhunButton
import dev.dhun.design.components.DhunSpinner
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.DhunTextField
import dev.dhun.lyrics.LyricsMatch
import dev.dhun.lyrics.TtmlParser
import dev.dhun.presentation.player.LyricsMatchController
import dev.dhun.presentation.player.LyricsMatchState
import dev.dhun.ui.components.TrackMenuSurface

/** First screen presented when the existing match picker is opened from a lyrics action. */
internal enum class LyricsMatchPickerStartMode { DEFAULT, SEARCH, EDIT }

/** UI dispatches state-machine actions only; no HTTP/client/repository calls here. */
@Composable
internal fun LyricsMatchPicker(
    track: Track,
    controller: LyricsMatchController,
    startMode: LyricsMatchPickerStartMode = LyricsMatchPickerStartMode.DEFAULT,
    onDismiss: () -> Unit,
) {
    val state by controller.state.collectAsState()
    var query by remember(track.id, startMode) { mutableStateOf("${track.artistName} ${track.title}".take(240)) }
    var showLocalImport by remember(track.id, startMode) {
        mutableStateOf(startMode == LyricsMatchPickerStartMode.EDIT)
    }
    var ttml by remember(track.id, startMode) { mutableStateOf("") }
    LaunchedEffect(track.id, startMode) {
        if (startMode == LyricsMatchPickerStartMode.SEARCH && query.isNotBlank()) {
            controller.search(track.id, query)
        }
    }
    val search: () -> Unit = {
        if (query.isNotBlank() && !state.loading) controller.search(track.id, query)
    }
    val artist = track.artistName.ifBlank { "Unknown artist" }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(horizontal = DhunSpacing.md, vertical = DhunSpacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            val surfaceMaxWidth = minOf(DhunSpacing.lyricsMatchDialogMaxWidth, maxWidth)
            val compactActions = maxWidth < DhunSpacing.lyricsMatchCompactActionBreakpoint
            val surfaceMaxHeight = (maxHeight - DhunSpacing.md).coerceAtLeast(DhunSpacing.zero)
            val bodyMaxHeight = (surfaceMaxHeight - DhunSpacing.smPlus * 2).coerceAtLeast(DhunSpacing.zero)
            TrackMenuSurface(
                artworkUrl = track.thumbnailUrl,
                modifier = Modifier
                    .widthIn(max = surfaceMaxWidth)
                    .fillMaxWidth()
                    .heightIn(max = surfaceMaxHeight),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = bodyMaxHeight)
                        .padding(horizontal = DhunSpacing.mdPlus),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = DhunSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
                    ) {
                        Text(
                            "Lyrics matches for this song",
                            style = MaterialTheme.typography.titleMedium,
                            color = DhunColors.textPrimary,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = "${track.title} · $artist",
                            style = MaterialTheme.typography.bodySmall,
                            color = DhunColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HorizontalDivider(color = DhunColors.border)
                    Column(
                        modifier = Modifier.weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = DhunSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
                    ) {
                        LyricsMatchSearchSection(
                            query = query,
                            onQueryChange = { query = it.take(240) },
                            onSearch = search,
                            onUseAutomatic = { controller.choose(track.id, null) },
                            enabled = !state.loading,
                            compactActions = compactActions,
                        )
                        LyricsLocalImportSection(
                            expanded = showLocalImport,
                            onToggle = { showLocalImport = !showLocalImport },
                            value = ttml,
                            onValueChange = { ttml = it.take(TtmlParser.MAX_INPUT_CHARS + 1) },
                            onPreview = { controller.previewTtml(track.id, ttml) },
                            canPreview = !state.loading && ttml.isNotBlank() && ttml.length <= TtmlParser.MAX_INPUT_CHARS,
                            overLimit = ttml.length > TtmlParser.MAX_INPUT_CHARS,
                        )
                        LyricsMatchStatus(state = state, track = track)
                        if (state.trackId == track.id && state.matches.isNotEmpty()) {
                            Text(
                                "Matches",
                                style = MaterialTheme.typography.titleSmall,
                                color = DhunColors.textPrimary,
                                modifier = Modifier.semantics { heading() },
                            )
                            state.matches.forEach { match ->
                                val isSelected = state.selected?.let {
                                    it.provider == match.provider && it.id == match.id
                                } == true
                                LyricsMatchResultCard(
                                    track = track,
                                    match = match,
                                    selected = isSelected,
                                    enabled = !state.loading,
                                    onChoose = { controller.choose(track.id, match) },
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = DhunSpacing.xs, bottom = DhunSpacing.sm),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        DhunTextButton(onClick = onDismiss) { Text("Close") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsMatchSearchSection(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onUseAutomatic: () -> Unit,
    enabled: Boolean,
    compactActions: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm)) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(DhunShapes.medium)
                .background(DhunColors.surfaceContainerLow)
                .padding(DhunSpacing.md),
            verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
        ) {
            Text("Search and matching", style = MaterialTheme.typography.labelMedium, color = DhunColors.accent)
            Text(
                "Search sends song metadata to your enabled lyrics providers. A saved choice overrides automatic matching for this song only.",
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.textSecondary,
            )
            Text(
                "Manual query search is supported by LRCLIB search; other sources retry the current track.",
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.textSecondary,
            )
        }
        DhunTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.semantics { contentDescription = "Search lyrics matches" },
            placeholder = "Title, artist or manual search",
            imeAction = ImeAction.Search,
            onSubmit = onSearch,
            leadingContent = {
                DhunIconView(
                    icon = DhunIcon.Search,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSizeSm),
                    tint = DhunColors.textTertiary,
                )
                Spacer(modifier = Modifier.width(DhunSpacing.sm))
            },
        )
        if (compactActions) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs)) {
                SearchLyricsButton(onSearch = onSearch, enabled = enabled, modifier = Modifier.fillMaxWidth())
                DhunTextButton(onClick = onUseAutomatic, enabled = enabled) { Text("Use automatic") }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchLyricsButton(onSearch = onSearch, enabled = enabled, modifier = Modifier.weight(1f))
                DhunTextButton(onClick = onUseAutomatic, enabled = enabled) { Text("Use automatic") }
            }
        }
    }
}

@Composable
private fun SearchLyricsButton(onSearch: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    DhunButton(
        onClick = onSearch,
        modifier = modifier,
        enabled = enabled,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = DhunSpacing.md,
            vertical = DhunSpacing.sm,
        ),
    ) {
        DhunIconView(
            icon = DhunIcon.Search,
            contentDescription = null,
            modifier = Modifier.size(DhunSpacing.iconSizeSm),
            tint = DhunColors.onAccent,
        )
        Spacer(modifier = Modifier.width(DhunSpacing.xs))
        Text("Search / Retry", maxLines = 1)
    }
}

@Composable
private fun LyricsLocalImportSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    onPreview: () -> Unit,
    canPreview: Boolean,
    overLimit: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(DhunShapes.medium)
            .background(DhunColors.surfaceContainerLow)
            .padding(DhunSpacing.md),
        verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
    ) {
        DhunTextButton(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth().semantics {
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            },
        ) {
            DhunIconView(
                icon = DhunIcon.Edit,
                contentDescription = null,
                modifier = Modifier.size(DhunSpacing.iconSizeSm),
                tint = DhunColors.accent,
            )
            Spacer(modifier = Modifier.width(DhunSpacing.xs))
            Text("Paste local TTML lyrics", maxLines = 1)
            Spacer(modifier = Modifier.weight(1f))
            Text(if (expanded) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.width(DhunSpacing.xs))
            DhunIconView(
                icon = DhunIcon.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(DhunSpacing.iconSizeSm),
                tint = DhunColors.accent,
            )
        }
        if (expanded) {
            Text(
                "Local only: paste TTML you have permission to use. Preview does not save or contact a provider. Unsupported XML/timing is rejected; use Choose this match after reviewing the preview.",
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.textSecondary,
            )
            Text(
                "TTML document",
                style = MaterialTheme.typography.labelSmall,
                color = DhunColors.textSecondary,
            )
            DhunTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.semantics { contentDescription = "TTML document" },
                placeholder = "TTML document (maximum 256,000 characters)",
                singleLine = false,
                minLines = 3,
                maxLines = 5,
            )
            if (overLimit) {
                Text(
                    "Document is too large. Paste a smaller document.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DhunColors.error,
                )
            }
            DhunTextButton(onClick = onPreview, enabled = canPreview) {
                Text("Preview local TTML")
            }
        }
    }
}

@Composable
private fun LyricsMatchStatus(state: LyricsMatchState, track: Track) {
    if (state.trackId == track.id) {
        state.selected?.let { choice ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(DhunShapes.medium)
                    .background(DhunColors.accentContainer)
                    .padding(horizontal = DhunSpacing.md, vertical = DhunSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DhunIconView(
                    icon = DhunIcon.Check,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSizeSm),
                    tint = DhunColors.accent,
                )
                Text(
                    "Saved choice: ${choice.provider} · ${choice.title}",
                    style = MaterialTheme.typography.labelMedium,
                    color = DhunColors.textPrimary,
                )
            }
        }
        state.message?.let {
            Text(
                it,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = DhunColors.textSecondary,
            )
        }
        if (state.failedProviders.isNotEmpty()) {
            Text(
                "Some providers failed or timed out: ${state.failedProviders.joinToString()}. You can retry.",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.error,
            )
        }
    }
    if (state.loading) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(DhunShapes.medium)
                .background(DhunColors.surfaceContainerLow)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(DhunSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DhunSpinner(modifier = Modifier.size(DhunSpacing.iconSizeSm), color = DhunColors.accent)
            Text(
                "Searching / parsing / saving…",
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.textSecondary,
            )
        }
    }
}

@Composable
private fun LyricsMatchResultCard(
    track: Track,
    match: LyricsMatch,
    selected: Boolean,
    enabled: Boolean,
    onChoose: () -> Unit,
) {
    val timing = if (match.synced) "Synced" else "Unsynced"
    val wordTimed = (match.lyrics as? Lyrics.Synced)?.lines?.any { it.words.isNotEmpty() } == true
    val provider = if (match.provider == "local_ttml") "Local TTML" else match.provider
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(DhunShapes.medium)
            .background(if (selected) DhunColors.accentContainer else DhunColors.surfaceContainerLow)
            .padding(DhunSpacing.md),
        verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
    ) {
        Text(
            text = match.title,
            style = MaterialTheme.typography.titleSmall,
            color = DhunColors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = match.artist,
            style = MaterialTheme.typography.bodySmall,
            color = DhunColors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "$provider · $timing${if (wordTimed) " (word timed)" else ""} · Language: ${match.language ?: "unknown"}",
            style = MaterialTheme.typography.labelSmall,
            color = DhunColors.textTertiary,
        )
        if (match.metadataVerified) {
            Text(
                "Title: ${if (match.title.equals(track.title, true)) "exact match" else "different"}; artist: ${if (match.artist.equals(track.artistName, true)) "exact match" else "different"}",
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.textSecondary,
            )
        } else {
            Text(
                if (match.provider == "local_ttml") {
                    "Local import for this song; title/artist are not verified against the document."
                } else {
                    "Requested track metadata; this source did not provide match details."
                },
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.textSecondary,
            )
        }
        Text(
            text = match.preview,
            style = MaterialTheme.typography.bodySmall,
            color = DhunColors.textPrimary,
        )
        DhunButton(
            onClick = onChoose,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
        ) {
            Text(if (selected) "Selected" else "Choose this match")
        }
    }
}
