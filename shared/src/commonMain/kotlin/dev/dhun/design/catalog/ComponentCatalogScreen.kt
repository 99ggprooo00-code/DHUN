package dev.dhun.design.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.dhun.core.Album
import dev.dhun.core.Artist
import dev.dhun.core.Playlist
import dev.dhun.core.Track
import dev.dhun.design.ArtworkColorExtractor
import dev.dhun.design.ArtworkColors
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunSpacing
import dev.dhun.design.DhunTheme
import dev.dhun.design.components.AlbumCard
import dev.dhun.design.components.ArtistCard
import dev.dhun.design.components.ArtworkImage
import dev.dhun.design.components.DhunButton
import dev.dhun.design.components.DhunFilterChip
import dev.dhun.design.components.DhunIconButton
import dev.dhun.design.components.DhunOutlinedButton
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.DhunTonalButton
import dev.dhun.design.components.EmptyView
import dev.dhun.design.components.ErrorView
import dev.dhun.design.components.GlassCard
import dev.dhun.design.components.PlaylistCard
import dev.dhun.design.components.SectionHeader
import dev.dhun.design.components.TrackCard
import dev.dhun.design.components.TrackRow
import dev.dhun.design.components.TrackRowShimmer

/**
 * ComponentCatalogScreen — renders every DHUN component in every state
 * over a colorful artwork background. This is the Phase-06 verification
 * surface: blur visibly real, no raw tokens outside design, artwork color
 * extraction sanity.
 *
 * Shown in debug builds via a `Catalog` entry point on both platforms.
 * Not shipped in release.
 */
@Composable
fun ComponentCatalogScreen(
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    DhunTheme {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DhunColors.background),
        ) {
            // Artwork-driven backdrop — colorful gradient standing in for real art.
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF6A1B9A),
                            Color(0xFF283593),
                            Color(0xFF00897B),
                            Color(0xFFF57F17),
                        ),
                    ),
                ),
            )
            // Scrim so foreground stays legible while still showing blur.
            Box(
                modifier = Modifier.fillMaxSize().background(DhunColors.scrim.copy(alpha = 0.35f)),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(DhunSpacing.lg),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(DhunSpacing.lg),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "DHUN Component Catalogue",
                            style = MaterialTheme.typography.headlineSmall,
                            color = DhunColors.textPrimary,
                        )
                        if (onClose != null) {
                            DhunTextButton(onClick = onClose) { Text("Close") }
                        }
                    }
                }

                // -- Tokens -------------------------------------------------
                item { SectionHeader(title = "Tokens — Colors") }
                item { ColorTokenRow() }

                item { SectionHeader(title = "GlassCard — real blur over artwork") }
                item { GlassCardDemo() }

                item { SectionHeader(title = "ArtworkImage — states") }
                item { ArtworkImageStates() }

                item { SectionHeader(title = "ArtworkColorExtractor — 5 seeds") }
                item { ArtworkColorDemo() }

                item { SectionHeader(title = "Buttons — all states") }
                item { ButtonStates() }

                item { SectionHeader(title = "Chips — filter / assist / input") }
                item { ChipStates() }

                item { SectionHeader(title = "TrackRow — all states") }
                item { TrackRowStates() }

                item { SectionHeader(title = "Cards — Track / Artist / Album / Playlist") }
                item { CardRow() }

                item { SectionHeader(title = "Loading — shimmer (not spinner)") }
                item { ShimmerDemo() }

                item { SectionHeader(title = "Error & Empty") }
                item { ErrorEmptyDemo() }

                item { Spacer(modifier = Modifier.height(DhunSpacing.xxl)) }
            }
        }
    }
}

@Composable
private fun ColorTokenRow() {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
    ) {
        val swatches = listOf(
            "background" to DhunColors.background, "surface" to DhunColors.surface,
            "surfaceVariant" to DhunColors.surfaceVariant, "surfaceElevated" to DhunColors.surfaceElevated,
            "glass" to DhunColors.glass, "accent" to DhunColors.accent, "error" to DhunColors.error,
        )
        items(swatches) { (name, color) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.width(64.dp).height(40.dp).background(color))
                Text(name, style = MaterialTheme.typography.labelSmall, color = DhunColors.textTertiary, modifier = Modifier.padding(top = DhunSpacing.xs))
            }
        }
    }
}

@Composable
private fun GlassCardDemo() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.lg), verticalArrangement = Arrangement.spacedBy(DhunSpacing.md)) {
        // Glass blur visibly over the gradient backdrop. On API <31 / non-Skiko,
        // this degrades to translucent scrim — still legible.
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(DhunSpacing.lg)) {
            Column(verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm)) {
                Text("GlassCard", style = MaterialTheme.typography.titleMedium, color = DhunColors.textPrimary)
                Text(
                    "Real blur (RenderEffect on Android 12+ / Desktop Skiko). Falls back to scrim+border below the floor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DhunColors.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm)) {
                    DhunButton(onClick = {}, enabled = true) { Text("Primary") }
                    DhunOutlinedButton(onClick = {}) { Text("Outline") }
                }
            }
        }
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(DhunSpacing.lg)) {
            Text("Second GlassCard — layered depth over artwork", color = DhunColors.textSecondary)
        }
    }
}

@Composable
private fun ArtworkImageStates() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.lg), horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md)) {
        // Loading placeholder (no url) — pulsing gradient
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ArtworkImage(imageUrl = null, contentDescription = null, modifier = Modifier.width(96.dp).height(96.dp))
            Text("empty (placeholder)", style = MaterialTheme.typography.labelSmall, color = DhunColors.textTertiary, modifier = Modifier.padding(top = DhunSpacing.xs))
        }
        // Loaded (real URL) — picsum provides colorful test art
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ArtworkImage(
                imageUrl = "https://picsum.photos/seed/dhun-catalog/200/200",
                contentDescription = "demo artwork",
                modifier = Modifier.width(96.dp).height(96.dp),
            )
            Text("loaded (picsum)", style = MaterialTheme.typography.labelSmall, color = DhunColors.textTertiary, modifier = Modifier.padding(top = DhunSpacing.xs))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ArtworkImage(
                imageUrl = "https://picsum.photos/seed/dhun-catalog-2/200/200",
                contentDescription = "demo artwork 2",
                modifier = Modifier.width(96.dp).height(96.dp),
            )
            Text("loaded", style = MaterialTheme.typography.labelSmall, color = DhunColors.textTertiary, modifier = Modifier.padding(top = DhunSpacing.xs))
        }
    }
}

@Composable
private fun ArtworkColorDemo() {
    val seeds = listOf("bohemian rhapsody queen", "blinding lights weeknd", "shape of you", "as it was harry styles", "dhun accent fallback")
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.lg), horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md)) {
        seeds.forEach { seed ->
            val colors: ArtworkColors = remember(seed) { ArtworkColorExtractor.extractFromSeed(seed) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.width(56.dp).height(56.dp).background(colors.primary))
                Box(modifier = Modifier.width(56.dp).height(12.dp).background(colors.backgroundTint))
                Text(seed.take(10), style = MaterialTheme.typography.labelSmall, color = DhunColors.textTertiary, modifier = Modifier.padding(top = DhunSpacing.xs))
            }
        }
    }
}

@Composable
private fun ButtonStates() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.lg), verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm)) {
            DhunButton(onClick = {}, enabled = true) { Text("Normal") }
            DhunButton(onClick = {}, enabled = false) { Text("Disabled") }
            DhunButton(onClick = {}, enabled = true, loading = true) { Text("Loading") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm)) {
            DhunTonalButton(onClick = {}) { Text("Tonal") }
            DhunOutlinedButton(onClick = {}) { Text("Outlined") }
            DhunTextButton(onClick = {}) { Text("Text") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text("IconButtons:", style = MaterialTheme.typography.labelMedium, color = DhunColors.textTertiary)
            DhunIconButton(onClick = {}, contentDescription = "Favorite") {
                DhunIconView(
                    icon = DhunIcon.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSize),
                    tint = DhunColors.accent,
                )
            }
            DhunIconButton(onClick = {}, enabled = false, contentDescription = "Favorite disabled") {
                DhunIconView(
                    icon = DhunIcon.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSize),
                    tint = DhunColors.textDisabled,
                )
            }
        }
    }
}

@Composable
private fun ChipStates() {
    var selected by remember { mutableStateOf(true) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.lg), verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm)) {
            DhunFilterChip(selected = selected, onClick = { selected = !selected }, label = { Text("Songs") })
            DhunFilterChip(selected = false, onClick = {}, label = { Text("Artists") })
            DhunFilterChip(selected = false, onClick = {}, enabled = false, label = { Text("Disabled") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm)) {
            Text("Chips exhaust normal/selected/disabled — pressed is the native ripple.", style = MaterialTheme.typography.bodySmall, color = DhunColors.textTertiary)
        }
    }
}

@Composable
private fun TrackRowStates() {
    val demo = Track(id = "demo", title = "Bohemian Rhapsody (Remastered 2011)", artistName = "Queen", albumName = "A Night at the Opera", durationSeconds = 354, thumbnailUrl = "https://picsum.photos/seed/queen/200/200")
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs)) {
        Text("Normal (clickable)", style = MaterialTheme.typography.labelSmall, color = DhunColors.textTertiary, modifier = Modifier.padding(horizontal = DhunSpacing.lg))
        TrackRow(track = demo, onClick = {}, enabled = true)
        Text("Disabled", style = MaterialTheme.typography.labelSmall, color = DhunColors.textTertiary, modifier = Modifier.padding(horizontal = DhunSpacing.lg))
        TrackRow(track = demo, onClick = {}, enabled = false)
        Text("Pressed = native ripple; loading = shimmer skeleton", style = MaterialTheme.typography.labelSmall, color = DhunColors.textTertiary, modifier = Modifier.padding(horizontal = DhunSpacing.lg))
        TrackRowShimmer(modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.lg))
    }
}

@Composable
private fun CardRow() {
    val track = Track(id = "t", title = "Blinding Lights", artistName = "The Weeknd", thumbnailUrl = "https://picsum.photos/seed/weeknd/300/300")
    val artist = Artist(id = "a", name = "Ariana Grande", thumbnailUrl = "https://picsum.photos/seed/ariana/300/300")
    val album = Album(id = "MPREb_test", title = "After Hours", artistName = "The Weeknd", thumbnailUrl = "https://picsum.photos/seed/afterhours/300/300")
    val playlist = Playlist(id = "VLtest", title = "Chill Mix", authorName = "YouTube Music", thumbnailUrl = "https://picsum.photos/seed/chill/300/300")
    LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DhunSpacing.md)) {
        item { TrackCard(track = track, onClick = {}) }
        item { ArtistCard(artist = artist, onClick = {}) }
        item { AlbumCard(album = album, onClick = {}) }
        item { PlaylistCard(playlist = playlist, onClick = {}) }
    }
}

@Composable
private fun ShimmerDemo() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.lg), verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm)) {
        repeat(2) { TrackRowShimmer(modifier = Modifier.fillMaxWidth()) }
        Text("Shimmer replaces spinners everywhere.", style = MaterialTheme.typography.bodySmall, color = DhunColors.textTertiary)
    }
}

@Composable
private fun ErrorEmptyDemo() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.lg), verticalArrangement = Arrangement.spacedBy(DhunSpacing.md)) {
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(DhunSpacing.lg)) {
            ErrorView(message = "You look offline. Check your connection and try again.", onRetry = {})
        }
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(DhunSpacing.lg)) {
            EmptyView(message = "No favorites yet. Tap the favorite icon on any track to save it here.", title = "No favorites", actionLabel = "Browse", onAction = {})
        }
    }
}
