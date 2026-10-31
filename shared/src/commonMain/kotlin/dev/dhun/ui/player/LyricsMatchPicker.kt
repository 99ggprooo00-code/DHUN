package dev.dhun.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.dhun.core.Track
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.DhunTextField
import dev.dhun.design.components.GlassCard
import dev.dhun.lyrics.TtmlParser
import dev.dhun.presentation.player.LyricsMatchController

/** UI dispatches state-machine actions only; no HTTP/client/repository calls here. */
@Composable
internal fun LyricsMatchPicker(track: Track, controller: LyricsMatchController, onDismiss: () -> Unit) {
    val state by controller.state.collectAsState()
    var query by remember(track.id) { mutableStateOf("${track.artistName} ${track.title}".take(240)) }
    var showLocalImport by remember(track.id) { mutableStateOf(false) }
    var ttml by remember(track.id) { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(modifier = Modifier.fillMaxWidth(), opaqueBase = true) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState()).padding(DhunSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
            ) {
                Text("Lyrics matches for this song", style = MaterialTheme.typography.titleLarge)
                Text("Search sends song metadata to your enabled lyrics providers. A saved choice overrides automatic matching for this song only.",
                    style = MaterialTheme.typography.bodySmall)
                Text("Manual query search is supported by LRCLIB search; other sources retry the current track.", style = MaterialTheme.typography.bodySmall)
                DhunTextField(value = query, onValueChange = { query = it.take(240) },
                    placeholder = "Title, artist or manual search")
                Row {
                    DhunTextButton(onClick = { controller.search(track.id, query) }, enabled = query.isNotBlank() && !state.loading) {
                        Text("Search / Retry")
                    }
                    DhunTextButton(onClick = { controller.choose(track.id, null) }, enabled = !state.loading) { Text("Use automatic") }
                }
                DhunTextButton(onClick = { showLocalImport = !showLocalImport }) { Text("Paste local TTML lyrics") }
                if (showLocalImport) {
                    Text("Local only: paste TTML you have permission to use. Preview does not save or contact a provider. Unsupported XML/timing is rejected; use Choose this match after reviewing the preview.",
                        style = MaterialTheme.typography.bodySmall)
                    DhunTextField(
                        value = ttml,
                        onValueChange = { ttml = it.take(TtmlParser.MAX_INPUT_CHARS + 1) },
                        placeholder = "TTML document (maximum 256,000 characters)",
                        singleLine = false,
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (ttml.length > TtmlParser.MAX_INPUT_CHARS) Text("Document is too large. Paste a smaller document.")
                    DhunTextButton(onClick = { controller.previewTtml(track.id, ttml) },
                        enabled = !state.loading && ttml.isNotBlank() && ttml.length <= TtmlParser.MAX_INPUT_CHARS) { Text("Preview local TTML") }
                }
                if (state.loading) Text("Searching / parsing / saving…", style = MaterialTheme.typography.bodySmall)
                if (state.trackId == track.id) {
                    state.selected?.let { Text("Saved choice: ${it.provider} · ${it.title}", style = MaterialTheme.typography.labelLarge) }
                    state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    if (state.failedProviders.isNotEmpty()) Text(
                        "Some providers failed or timed out: ${state.failedProviders.joinToString()}. You can retry.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    state.matches.forEach { match ->
                        HorizontalDivider()
                        Text("${match.title} — ${match.artist}", style = MaterialTheme.typography.titleMedium)
                        val timing = if (match.synced) "Synced" else "Unsynced"
                        val wordTimed = (match.lyrics as? dev.dhun.core.Lyrics.Synced)?.lines?.any { it.words.isNotEmpty() } == true
                        Text("${if (match.provider == "local_ttml") "Local TTML" else match.provider} · $timing${if (wordTimed) " (word timed)" else ""} · Language: ${match.language ?: "unknown"}",
                            style = MaterialTheme.typography.labelMedium)
                        if (match.metadataVerified) Text(
                            "Title: ${if (match.title.equals(track.title, true)) "exact match" else "different"}; artist: ${if (match.artist.equals(track.artistName, true)) "exact match" else "different"}",
                            style = MaterialTheme.typography.bodySmall,
                        ) else Text(if (match.provider == "local_ttml") "Local import for this song; title/artist are not verified against the document."
                            else "Requested track metadata; this source did not provide match details.", style = MaterialTheme.typography.bodySmall)
                        Text(match.preview, style = MaterialTheme.typography.bodyMedium)
                        DhunTextButton(onClick = { controller.choose(track.id, match) }, enabled = !state.loading) {
                            Text(if (state.selected?.let { it.provider == match.provider && it.id == match.id } == true) "Selected" else "Choose this match")
                        }
                    }
                }
                DhunTextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}
