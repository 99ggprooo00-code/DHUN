package dev.dhun.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dhun.core.PlaybackState
import dev.dhun.player.DhunPlayer
import kotlinx.coroutines.launch

/**
 * PHASE 03 TEST HARNESS SCREEN — deliberately plain. Verifies search ->
 * queue -> playback -> transport -> seek -> state flows end-to-end on real
 * hardware. Replaced by the real UI in later phases.
 */
@Composable
fun HarnessScreen(player: DhunPlayer, viewModel: HarnessViewModel) {
    val ui by viewModel.state.collectAsState()
    val state by player.state.collectAsState()
    val current by player.currentTrack.collectAsState()
    val position by player.positionMs.collectAsState()
    val duration by player.durationMs.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("DHUN", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Phase 03 test harness — throwaway",
                    fontSize = 12.sp,
                    color = Color(0xFF888888),
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
                    Text(it, color = Color(0xFFCF6679), modifier = Modifier.padding(top = 8.dp))
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { scope.launch { player.prepareQueue(ui.tracks, index) } }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(track.title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "${track.artistName}" +
                                    (track.albumName?.let { " • $it" } ?: "") +
                                    (track.durationSeconds?.let { " • ${formatSeconds(it)}" } ?: ""),
                                fontSize = 12.sp,
                                color = Color(0xFFAAAAAA),
                            )
                        }
                        HorizontalDivider(color = Color(0xFF222222))
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
                is PlaybackState.Playing -> "▶ playing"
                is PlaybackState.Paused -> "⏸ paused"
                is PlaybackState.Buffering -> "… buffering"
                is PlaybackState.Resolving -> "… resolving"
                is PlaybackState.Error -> "✕ ${state.message}"
                PlaybackState.Idle -> "idle"
            },
            fontSize = 12.sp,
            color = Color(0xFF888888),
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
            TextButton(onClick = onPrevious) { Text("⏮", fontSize = 20.sp) }
            TextButton(onClick = onPlayPause) {
                Text(
                    if (state is PlaybackState.Playing) "⏸" else "▶",
                    fontSize = 24.sp,
                )
            }
            TextButton(onClick = onNext) { Text("⏭", fontSize = 20.sp) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMs(position), fontSize = 11.sp, color = Color(0xFF777777))
            Text(formatMs(duration), fontSize = 11.sp, color = Color(0xFF777777))
        }
    }
}

@Composable
fun ConnectingScreen() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("DHUN", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("connecting to playback service…", color = Color(0xFF888888))
            }
        }
    }
}

private fun formatSeconds(total: Int): String =
    "%d:%02d".format(total / 60, total % 60)

private fun formatMs(ms: Long): String =
    formatSeconds((ms / 1000).toInt().coerceAtLeast(0))
