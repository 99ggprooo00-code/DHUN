package dev.dhun.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dhun.core.PlaybackState
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.DhunIconButton
import dev.dhun.player.DhunPlayer
import kotlinx.coroutines.launch

/**
 * PHASE 04 TEST HARNESS SCREEN — deliberately plain, mirrors the Android
 * Phase-03 harness so the same verification loop runs on the desktop:
 * search -> queue -> playback -> transport -> seek -> state flows, against
 * the same shared [DhunPlayer] contract. Throwaway; replaced by the real UI
 * in later phases.
 */
@Composable
fun DesktopHarnessScreen(player: DhunPlayer, viewModel: DesktopHarnessViewModel) {
    val ui by viewModel.state.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val state by player.state.collectAsState()
    val current by player.currentTrack.collectAsState()
    val position by player.positionMs.collectAsState()
    val duration by player.durationMs.collectAsState()
    val scope = rememberCoroutineScope()

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("DHUN", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Phase 04 test harness — throwaway",
                    fontSize = 12.sp,
                    color = Color(0xFF888888.toInt()),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = ui.query,
                        onValueChange = viewModel::onQueryChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Search YouTube Music") },
                    )
                    Button(onClick = viewModel::search, enabled = !ui.loading) {
                        Text("Go")
                    }
                }

                ui.error?.let {
                    Text(it, color = Color(0xFFCF6679.toInt()), modifier = Modifier.padding(top = 8.dp))
                }

                // Phase 05 verification strip: recent searches + "listen again"
                // come from the local database and must survive a restart.
                if (recentSearches.isNotEmpty()) {
                    Text("Recent: " + recentSearches.joinToString(" · "), fontSize = 11.sp, color = Color(0xFF777777.toInt()))
                }
                if (recentlyPlayed.isNotEmpty()) {
                    Text("Listen again", fontSize = 12.sp, color = Color(0xFF888888.toInt()), modifier = Modifier.padding(top = 6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recentlyPlayed, key = { it.id }) { t ->
                            Surface(
                                color = Color(0xFF1E1E1E.toInt()),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.clickable { scope.launch { player.prepareQueue(recentlyPlayed, recentlyPlayed.indexOf(t)) } },
                            ) {
                                Text(t.title.take(22), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                    }
                }

                if (ui.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(top = 16.dp).size(28.dp),
                        strokeWidth = 3.dp,
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
                ) {
                    itemsIndexed(ui.tracks) { index, track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { scope.launch { player.prepareQueue(ui.tracks, index) } }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "${track.artistName}" +
                                        (track.albumName?.let { " • $it" } ?: "") +
                                        (track.durationSeconds?.let { " • ${formatSeconds(it)}" } ?: ""),
                                    fontSize = 12.sp,
                                    color = Color(0xFFAAAAAA.toInt()),
                                )
                            }
                            val fav = track.id in favoriteIds
                            DhunIconButton(
                                onClick = { viewModel.toggleFavorite(track) },
                                modifier = Modifier.size(DhunSpacing.touchTarget),
                                contentDescription = if (fav) "Remove ${track.title} from favorites" else "Add ${track.title} to favorites",
                            ) {
                                DhunIconView(
                                    icon = if (fav) DhunIcon.Favorite else DhunIcon.FavoriteBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(DhunSpacing.iconSize),
                                    tint = if (fav) DhunColors.accent else DhunColors.textTertiary,
                                )
                            }
                        }
                        HorizontalDivider(color = Color(0xFF222222.toInt()))
                    }
                }

                NowPlayingBar(
                    state = state,
                    current = current,
                    position = position,
                    duration = duration,
                    onPlayPause = player::playPause,
                    onNext = player::next,
                    onPrevious = player::previous,
                    onSeek = player::seekTo,
                )
            }
        }
    }
}

@Composable
private fun NowPlayingBar(
    state: PlaybackState,
    current: dev.dhun.core.Track?,
    position: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            when (state) {
                is PlaybackState.Playing -> "Playing"
                is PlaybackState.Paused -> "Paused"
                is PlaybackState.Buffering -> "Buffering"
                is PlaybackState.Resolving -> "Resolving"
                is PlaybackState.Error -> "Error: ${state.message}"
                PlaybackState.Idle -> "idle"
            },
            fontSize = 12.sp,
            color = Color(0xFF888888.toInt()),
        )
        Text(
            current?.let { "${it.title} — ${it.artistName}" } ?: "Nothing playing",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Slider(
            value = position.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
            enabled = duration > 0,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DhunIconButton(
                onClick = onPrevious,
                modifier = Modifier.size(DhunSpacing.touchTarget),
                contentDescription = "Previous track",
            ) {
                DhunIconView(
                    icon = DhunIcon.SkipPrevious,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSize),
                    tint = DhunColors.textPrimary,
                )
            }
            DhunIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(DhunSpacing.touchTarget),
                contentDescription = if (state is PlaybackState.Playing) "Pause" else "Play",
            ) {
                DhunIconView(
                    icon = if (state is PlaybackState.Playing) DhunIcon.Pause else DhunIcon.Play,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSizeLg),
                    tint = DhunColors.textPrimary,
                )
            }
            DhunIconButton(
                onClick = onNext,
                modifier = Modifier.size(DhunSpacing.touchTarget),
                contentDescription = "Next track",
            ) {
                DhunIconView(
                    icon = DhunIcon.SkipNext,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSize),
                    tint = DhunColors.textPrimary,
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMs(position), fontSize = 11.sp, color = Color(0xFF777777.toInt()))
            Text(formatMs(duration), fontSize = 11.sp, color = Color(0xFF777777.toInt()))
        }
    }
}

private fun formatSeconds(total: Int): String =
    "%d:%02d".format(total / 60, total % 60)

private fun formatMs(ms: Long): String =
    formatSeconds((ms / 1000).toInt().coerceAtLeast(0))
