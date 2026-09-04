package dev.dhun.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.dhun.core.Track
import dev.dhun.data.LocalPlaylist
import dev.dhun.data.PlaylistRepository
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.DhunButton
import dev.dhun.design.components.DhunOutlinedButton
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.GlassCard
import kotlinx.coroutines.launch

@Composable
fun AddToPlaylistDialog(
    track: Track,
    playlistRepository: PlaylistRepository,
    onDismiss: () -> Unit,
    onAdded: (playlistName: String) -> Unit,
    /** Phase 09: breadcrumb into the local playlist page after adding. */
    onOpenPlaylist: ((playlistId: String) -> Unit)? = null,
) {
    val playlists by playlistRepository.observePlaylists().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var isCreatingNew by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 400.dp)
                .padding(DhunSpacing.md),
            shape = DhunShapes.large,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DhunSpacing.lg),
            ) {
                Text(
                    text = if (isCreatingNew) "New Playlist" else "Add to Playlist",
                    style = MaterialTheme.typography.titleLarge,
                    color = DhunColors.textPrimary,
                )
                Spacer(modifier = Modifier.height(DhunSpacing.xs))
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = DhunColors.textTertiary,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(DhunSpacing.md))

                if (isCreatingNew) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = {
                            newPlaylistName = it
                            errorText = null
                        },
                        label = { Text("Playlist name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    errorText?.let {
                        Text(
                            text = it,
                            color = DhunColors.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = DhunSpacing.xs),
                        )
                    }
                    Spacer(modifier = Modifier.height(DhunSpacing.lg))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        DhunTextButton(onClick = { isCreatingNew = false }) {
                            Text("Back")
                        }
                        Spacer(modifier = Modifier.size(DhunSpacing.sm))
                        DhunButton(
                            onClick = {
                                val name = newPlaylistName.trim()
                                if (name.isBlank()) {
                                    errorText = "Name cannot be empty"
                                    return@DhunButton
                                }
                                scope.launch {
                                    val playlist = playlistRepository.create(name)
                                    playlistRepository.addTrack(playlist.id, track)
                                    onAdded(playlist.name)
                                    onDismiss()
                                }
                            },
                        ) {
                            Text("Create & Add")
                        }
                    }
                } else {
                    if (playlists.isEmpty()) {
                        Text(
                            text = "No playlists found. Create one below!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DhunColors.textSecondary,
                            modifier = Modifier.padding(vertical = DhunSpacing.md),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                        ) {
                            items(playlists, key = { it.id }) { playlist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                playlistRepository.addTrack(playlist.id, track)
                                                onAdded(playlist.name)
                                                onDismiss()
                                            }
                                        }
                                        .padding(vertical = DhunSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "☰",
                                        color = DhunColors.accent,
                                        fontSize = 18.sp,
                                        modifier = Modifier.padding(end = DhunSpacing.md),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = DhunColors.textPrimary,
                                        )
                                        Text(
                                            text = "${playlist.trackCount} tracks",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DhunColors.textTertiary,
                                        )
                                    }
                                    if (onOpenPlaylist != null) {
                                        DhunTextButton(
                                            onClick = { onOpenPlaylist(playlist.id) },
                                        ) {
                                            Text("Open", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(DhunSpacing.md))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        DhunOutlinedButton(onClick = { isCreatingNew = true }) {
                            Text("+ New Playlist")
                        }
                        DhunTextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }
}
