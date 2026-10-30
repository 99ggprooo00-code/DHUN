package dev.dhun.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.background.BackgroundPalette
import dev.dhun.design.background.BackgroundSection
import dev.dhun.design.background.BackgroundState
import dev.dhun.design.background.DhunBackgrounds
import dev.dhun.design.components.DhunButton
import dev.dhun.design.components.DhunSwitch
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.GlassDialog
import dev.dhun.presentation.settings.SettingsViewModel

/**
 * The Background preferences, in the order a user thinks about them:
 *
 * 1. **Background blur** — the master. Off means none, anywhere.
 * 2. **Dynamic blur** — *how* blur behaves; only meaningful while the master is
 *    on, so it is disabled (not hidden) when the master is off. That keeps the
 *    saved preference visible and restorable instead of silently forgetting it.
 * 3. **Dynamic colors** — a separate feature. It says nothing about blur.
 * 4. **Per-section** — Home, Search, Library, Miniplayer, Full player.
 *
 * Every caption names the *effective* source of the colours, so a global
 * override never looks like a broken section setting.
 */
@Composable
internal fun BackgroundSettingsSection(viewModel: SettingsViewModel) {
    val backgrounds by viewModel.backgrounds.collectAsState()
    var openSection by remember { mutableStateOf<BackgroundSection?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
    ) {
        BackgroundSwitchRow(
            title = "Background blur",
            subtitle = if (backgrounds.blurEnabled) {
                "Frost the artwork behind DHUN's glass surfaces."
            } else {
                "Off: no background blur anywhere. Artwork colours and translucent surfaces stay exactly as they are."
            },
            checked = backgrounds.blurEnabled,
            onCheckedChange = viewModel::setBackgroundBlurEnabled,
        )
        BackgroundSwitchRow(
            title = "Dynamic blur",
            subtitle = when {
                !backgrounds.blurEnabled -> "Paused while Background blur is off. Your choice is remembered."
                backgrounds.dynamicBlur -> "The frost adapts to each cover: brighter, busier artwork gets more blur."
                else -> "One steady frost for every cover."
            },
            checked = backgrounds.dynamicBlur,
            enabled = backgrounds.blurEnabled,
            onCheckedChange = viewModel::setBackgroundDynamicBlur,
        )
        BackgroundSwitchRow(
            title = "Dynamic colors",
            subtitle = if (backgrounds.dynamicColors) {
                "Backgrounds take their colour from the current artwork. Independent of blur."
            } else {
                "Off: every section uses its own saved colours (or the DHUN defaults)."
            },
            checked = backgrounds.dynamicColors,
            onCheckedChange = viewModel::setBackgroundDynamicColors,
        )

        Spacer(modifier = Modifier.height(DhunSpacing.xs))
        Text(
            text = "Section backgrounds",
            style = MaterialTheme.typography.titleSmall,
            color = DhunColors.textPrimary,
        )
        Text(
            text = "Each surface can keep its own colours and blur, even while the switches above are on.",
            style = MaterialTheme.typography.bodySmall,
            color = DhunColors.textSecondary,
        )

        for (section in BackgroundSection.entries) {
            BackgroundSectionRow(
                section = section,
                state = backgrounds,
                onClick = { openSection = section },
            )
        }
        DhunTextButton(onClick = viewModel::resetAllSectionBackgrounds) {
            Text("Restore all section defaults")
        }
    }

    openSection?.let { section ->
        BackgroundSectionDialog(
            section = section,
            viewModel = viewModel,
            onDismiss = { openSection = null },
        )
    }
}

/* --------------------------------------------------------------- rows */

@Composable
private fun BackgroundSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = DhunSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) DhunColors.textPrimary else DhunColors.textDisabled,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) DhunColors.textSecondary else DhunColors.textTertiary,
            )
        }
        Spacer(modifier = Modifier.width(DhunSpacing.md))
        DhunSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/**
 * One section: its palette preview, what is actually in effect, and the way in.
 * The preview is painted from the *resolved* palette, not the saved one, so
 * what the user sees here is what the app paints behind that surface.
 */
@Composable
private fun BackgroundSectionRow(
    section: BackgroundSection,
    state: BackgroundState,
    onClick: () -> Unit,
) {
    val prefs = state.section(section)
    // Read the *live* holder rather than the saved snapshot: the artwork palette
    // is not a persisted preference, and it changes when the song does, so this
    // is what keeps the preview honest while Settings is open. Reading it also
    // subscribes the row, so a track change repaints the preview.
    val palette = DhunBackgrounds.paletteFor(section)
    val source = DhunBackgrounds.sourceFor(section)
    // What this surface will actually render right now (0 = sharp), so the row
    // can say so instead of making the user open the dialog to find out.
    val blur = DhunBackgrounds.blurPercentFor(section)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DhunShapes.large)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = DhunSpacing.sm, horizontal = DhunSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PalettePreview(palette = palette, modifier = Modifier.width(56.dp).height(32.dp))
        Spacer(modifier = Modifier.width(DhunSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = section.label, style = MaterialTheme.typography.bodyLarge, color = DhunColors.textPrimary)
            Text(
                text = buildString {
                    append(source.label)
                    append(" · ")
                    append(if (blur > 0) "blur on" else "no blur")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (source.isOverridden) DhunColors.warning else DhunColors.textSecondary,
            )
        }
        Text(
            text = "Customise",
            style = MaterialTheme.typography.labelLarge,
            color = DhunColors.accent,
        )
    }
}

/* ------------------------------------------------------------- dialog */

@Composable
private fun BackgroundSectionDialog(
    section: BackgroundSection,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.backgrounds.collectAsState()
    val prefs = state.section(section)
    // Same live read as the row: the dialog previews what the app paints.
    val palette = DhunBackgrounds.paletteFor(section)
    val source = DhunBackgrounds.sourceFor(section)

    GlassDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = DhunSpacing.dialogMaxWidth),
            verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
        ) {
            Text(section.label, style = MaterialTheme.typography.titleLarge, color = DhunColors.textPrimary)
            Text(
                text = section.description,
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.textSecondary,
            )
            PalettePreview(palette = palette, modifier = Modifier.fillMaxWidth().height(48.dp))
            Text(
                text = "Showing: ${source.label}",
                style = MaterialTheme.typography.labelMedium,
                color = if (source.isOverridden) DhunColors.warning else DhunColors.textSecondary,
            )

            BackgroundSwitchRow(
                title = "Blur on this surface",
                subtitle = if (state.blurEnabled) {
                    "Turn it off to keep this surface sharp while the rest of DHUN is frosted."
                } else {
                    "Background blur is off globally, so nothing is blurred right now."
                },
                checked = prefs.blurEnabled,
                enabled = state.blurEnabled,
                onCheckedChange = { viewModel.setSectionBlurEnabled(section, it) },
            )
            BackgroundSwitchRow(
                title = "Dynamic blur here",
                subtitle = "Blur that follows the artwork on this surface only.",
                checked = prefs.dynamicBlur,
                enabled = state.blurEnabled && state.dynamicBlur && prefs.blurEnabled,
                onCheckedChange = { viewModel.setSectionDynamicBlur(section, it) },
            )
            BackgroundSwitchRow(
                title = "Artwork colors here",
                subtitle = if (state.dynamicColors) {
                    "Use this song's colours for this surface only."
                } else {
                    "Dynamic colors are off globally; this surface uses your colours."
                },
                checked = prefs.dynamicColors,
                enabled = state.dynamicColors,
                onCheckedChange = { viewModel.setSectionDynamicColors(section, it) },
            )

            Text(
                text = "Your colours for ${section.label}",
                style = MaterialTheme.typography.titleSmall,
                color = DhunColors.textPrimary,
            )
            Text(
                text = "Used whenever this surface is not following the artwork.",
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.textSecondary,
            )
            for (layer in PaletteLayer.entries) {
                ColorLayerPicker(
                    layer = layer,
                    selected = prefs.manual?.get(layer) ?: palette.get(layer),
                    onPick = { color ->
                        val current = prefs.manual ?: palette
                        viewModel.setSectionManualPalette(section, current.with(layer, color))
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DhunTextButton(onClick = { viewModel.resetSectionBackgrounds(section) }) {
                    Text("Restore defaults")
                }
                Spacer(modifier = Modifier.width(DhunSpacing.sm))
                DhunButton(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}

/**
 * One layer of the palette: a name, the current swatch, and a row of picks.
 *
 * The picker is a fixed set rather than a free colour wheel on purpose: every
 * swatch is already inside the luminance band the contrast gates were measured
 * against, so "choose any colour" cannot produce an unreadable app, and the
 * three layers stay a palette instead of three unrelated hues.
 */
@Composable
private fun ColorLayerPicker(
    layer: PaletteLayer,
    selected: Color,
    onPick: (Color) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = layer.label,
                style = MaterialTheme.typography.bodyMedium,
                color = DhunColors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(DhunSpacing.xl)
                    .clip(CircleShape)
                    .background(selected)
                    .border(DhunSpacing.border, DhunColors.glassEdge, CircleShape),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm)) {
            for (swatch in PaletteLayer.swatches(layer)) {
                val isSelected = swatch == selected
                Box(
                    modifier = Modifier
                        .size(DhunSpacing.xxl + DhunSpacing.xs)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(
                            width = if (isSelected) DhunSpacing.divider * 2 else DhunSpacing.border,
                            color = if (isSelected) DhunColors.accent else DhunColors.glassEdge,
                            shape = CircleShape,
                        )
                        .clickable(role = Role.RadioButton) { onPick(swatch) }
                        .semantics { contentDescription = "${layer.label} colour" },
                )
            }
        }
    }
}

/** The three palette layers, in painting order. */
private enum class PaletteLayer(val label: String) {
    PRIMARY("Primary (base wash)"),
    SECONDARY("Secondary (main glow)"),
    TERTIARY("Tertiary (companion glow)"),
    ;

    companion object {
        /**
         * The picks offered for a layer. Kept short: three rows of twelve is a
         * chooser, three rows of forty is a colour picker the user has to learn.
         */
        fun swatches(layer: PaletteLayer): List<Color> = when (layer) {
            PRIMARY -> baseSwatches
            SECONDARY -> accentSwatches
            TERTIARY -> accentSwatches
        }

        private val baseSwatches = listOf(
            Color(0xFF101014), Color(0xFF161616), Color(0xFF1B1E24), Color(0xFF221C2A),
            Color(0xFF16211F), Color(0xFF241A1A), Color(0xFF1A2230), Color(0xFF2A2418),
            Color(0xFF0B0B0F), Color(0xFF23262B), Color(0xFF2B2135), Color(0xFF12232B),
        )

        private val accentSwatches = listOf(
            Color(0xFFBB86FC), Color(0xFF7C9CF5), Color(0xFF4FC3A1), Color(0xFFE0A458),
            Color(0xFFE07A9B), Color(0xFF8AD8E8), Color(0xFFA0D468), Color(0xFFC79BF0),
            Color(0xFF80CBC4), Color(0xFFF2C14E), Color(0xFF9FA8DA), Color(0xFFD98880),
        )
    }
}

private fun BackgroundPalette.get(layer: PaletteLayer): Color = when (layer) {
    PaletteLayer.PRIMARY -> primary
    PaletteLayer.SECONDARY -> secondary
    PaletteLayer.TERTIARY -> tertiary
}

private fun BackgroundPalette.with(layer: PaletteLayer, color: Color): BackgroundPalette = when (layer) {
    PaletteLayer.PRIMARY -> copy(primary = color)
    PaletteLayer.SECONDARY -> copy(secondary = color)
    PaletteLayer.TERTIARY -> copy(tertiary = color)
}

/** The palette as the user will meet it: base behind, two glows in front. */
@Composable
private fun PalettePreview(palette: BackgroundPalette, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(DhunShapes.medium)
            .background(
                Brush.linearGradient(
                    listOf(
                        palette.primary,
                        blend(palette.primary, palette.secondary, 0.55f),
                        blend(palette.primary, palette.tertiary, 0.55f),
                    ),
                ),
            )
            .border(DhunSpacing.border, DhunColors.glassEdge, DhunShapes.medium),
    )
}

private fun blend(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)
