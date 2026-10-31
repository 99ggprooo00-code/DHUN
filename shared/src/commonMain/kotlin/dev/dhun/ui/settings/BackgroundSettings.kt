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
import dev.dhun.design.DhunAccent
import dev.dhun.design.DhunAppearance
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.hueSliderColors
import dev.dhun.design.lightnessSliderColors
import dev.dhun.design.saturationSliderColors
import dev.dhun.design.toHsl
import dev.dhun.design.background.BackgroundComponent
import dev.dhun.design.background.BackgroundPalette
import dev.dhun.design.background.BackgroundPalettePolicy
import dev.dhun.design.background.BackgroundState
import dev.dhun.design.background.DhunBackgrounds
import dev.dhun.design.components.DhunButton
import dev.dhun.design.components.DhunColourSlider
import dev.dhun.design.components.DhunIconButton
import dev.dhun.design.components.DhunSwitch
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.GlassDialog
import dev.dhun.design.supportsRealtimeBlur
import dev.dhun.presentation.settings.SettingsViewModel
import kotlin.math.roundToInt

/**
 * Background preferences, in the order the two decisions actually are:
 *
 * 1. **Background blur** — is the playing track's artwork behind the app?
 *    Off means it is not drawn at all, not that it is drawn sharper.
 * 2. **Dynamic colours** — do the surfaces take their colour from that artwork?
 *    This is independent: colour works with the artwork removed.
 * 3. **Surface colours** — a colour each for the pages, the navigation bar, the
 *    miniplayer and the full player. Used on their own when Dynamic colours is
 *    off, and as the tint over the blurred artwork when it is on.
 *
 * There is deliberately no `primary`/`secondary`/`glow` picker here: the user
 * chooses the appearance of interface *areas*, and the three-layer palette the
 * background is painted from is derived from that one choice.
 */
@Composable
internal fun BackgroundSettingsSection(viewModel: SettingsViewModel) {
    val backgrounds by viewModel.backgrounds.collectAsState()
    var openComponent by remember { mutableStateOf<BackgroundComponent?>(null) }
    var confirmResetAll by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = DhunSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
    ) {
        BackgroundSwitchRow(
            title = "Background blur",
            subtitle = if (backgrounds.blurEnabled) {
                "The playing track's artwork is used as a blurred background, tinted with each surface's colour."
            } else {
                "Off: no artwork behind the app at all. Every surface uses its own solid colour."
            },
            checked = backgrounds.blurEnabled,
            onCheckedChange = viewModel::setBackgroundBlurEnabled,
        )
        // Honest, not silent: on a device without real blur (Android < 12) the
        // switches still save the preference, but the cover is shown dimmed
        // instead of frosted — the documented fallback.
        if (!supportsRealtimeBlur) {
            Text(
                text = "This device cannot blur artwork. Your choices are saved, and the cover is dimmed instead of frosted.",
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.textTertiary,
            )
        }
        BackgroundSwitchRow(
            title = "Dynamic colours",
            subtitle = if (backgrounds.dynamicColors) {
                "Surface colours come from the playing track's artwork. Works with or without background blur."
            } else {
                "Off: every surface uses the colour you pick below, and artwork colour is ignored."
            },
            checked = backgrounds.dynamicColors,
            onCheckedChange = viewModel::setBackgroundDynamicColors,
        )

        Spacer(modifier = Modifier.height(DhunSpacing.xs))
        Text(
            text = "Surface colours",
            style = MaterialTheme.typography.titleSmall,
            color = DhunColors.textPrimary,
        )
        Text(
            text = "A colour for each part of the interface. With background blur on it tints the blurred artwork; " +
                "with blur off it is the surface itself. The two switches above are global; each surface below can " +
                "also opt out of artwork colour or drop its own blur.",
            style = MaterialTheme.typography.bodySmall,
            color = DhunColors.textSecondary,
        )

        for (component in BackgroundComponent.entries) {
            BackgroundComponentRow(
                component = component,
                state = backgrounds,
                onClick = { openComponent = component },
            )
        }
        // Confirmed, because it clears all four surfaces at once and there is no
        // undo — a single tap next to four rows is too easy to hit by accident.
        DhunTextButton(onClick = { confirmResetAll = true }) {
            Text("Restore all surface colours")
        }
    }

    openComponent?.let { component ->
        BackgroundComponentDialog(
            component = component,
            viewModel = viewModel,
            onDismiss = { openComponent = null },
        )
    }

    if (confirmResetAll) {
        GlassDialog(onDismissRequest = { confirmResetAll = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = DhunSpacing.dialogMaxWidth),
                verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
            ) {
                Text(
                    text = "Restore every surface?",
                    style = MaterialTheme.typography.titleLarge,
                    color = DhunColors.textPrimary,
                )
                Text(
                    text = "Main pages, Navigation bar, Miniplayer and Full player go back to their default " +
                        "colours and blur. The two switches above keep their current settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DhunColors.textSecondary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    DhunTextButton(onClick = { confirmResetAll = false }) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(DhunSpacing.sm))
                    DhunButton(
                        onClick = {
                            viewModel.resetAllComponentBackgrounds()
                            confirmResetAll = false
                        },
                    ) { Text("Restore") }
                }
            }
        }
    }
}

/* --------------------------------------------------------------- rows */

@Composable
internal fun BackgroundSwitchRow(
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
 * One surface: its colour preview, what is actually in effect, and the way in.
 * The preview is painted from the *resolved* palette, not the saved one, so what
 * the user sees here is what the app paints behind that surface.
 */
@Composable
private fun BackgroundComponentRow(
    component: BackgroundComponent,
    state: BackgroundState,
    onClick: () -> Unit,
) {
    val prefs = state.component(component)
    // Read the live holder rather than the saved snapshot: the artwork colour is
    // not a persisted preference and changes when the song does, so this is what
    // keeps the preview honest while Settings is open.
    val palette = DhunBackgrounds.paletteFor(component)
    val source = DhunBackgrounds.sourceFor(component)
    val showsArtwork = DhunBackgrounds.showsArtwork(component)
    val blurLabel = when {
        showsArtwork && state.adaptiveBlur -> "artwork behind it"
        showsArtwork -> "artwork behind it, steady frost"
        // Distinguish "this surface is set sharp" from "the switch above is
        // holding it": the second one is an override the user should see without
        // opening the dialog.
        prefs.blurEnabled && !state.blurEnabled -> "blur paused"
        else -> "solid colour"
    }
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
            Text(
                text = component.label,
                style = MaterialTheme.typography.bodyLarge,
                color = DhunColors.textPrimary,
            )
            Text(
                text = buildString {
                    append(source.label)
                    append(" · ")
                    append(blurLabel)
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
private fun BackgroundComponentDialog(
    component: BackgroundComponent,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.backgrounds.collectAsState()
    val prefs = state.component(component)
    val palette = DhunBackgrounds.paletteFor(component)
    val source = DhunBackgrounds.sourceFor(component)
    val tokens = DhunAppearance.tokens
    // The swatch that is "selected" is the stored colour, not the colour in
    // effect: while Dynamic colours is on, the surface is showing artwork
    // colours, and pretending a swatch is selected would be a lie.
    val picked = prefs.color
    val defaultPreview = BackgroundPalettePolicy.defaultPalette(component, tokens).mainGlow
    // The editor stores an opaque RGB colour; the quick Default swatch keeps
    // the palette's own alpha so its preview remains unchanged.
    val defaultColour = defaultPreview.copy(alpha = 1f)
    var customColour by remember { mutableStateOf(false) }

    GlassDialog(onDismissRequest = onDismiss) {
        if (customColour) {
            CustomColourEditor(
                initialColor = picked ?: defaultColour,
                onBack = { customColour = false },
                onUseColour = { color ->
                    viewModel.setComponentColor(component, color)
                    customColour = false
                },
            )
            return@GlassDialog
        }
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = DhunSpacing.dialogMaxWidth),
            verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
        ) {
            Text(component.label, style = MaterialTheme.typography.titleLarge, color = DhunColors.textPrimary)
            Text(
                text = component.description,
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.textSecondary,
            )
            PalettePreview(palette = palette, modifier = Modifier.fillMaxWidth().height(48.dp))
            Text(
                text = "Showing: ${source.label}",
                style = MaterialTheme.typography.labelMedium,
                color = if (source.isOverridden) DhunColors.warning else DhunColors.textSecondary,
            )

            // The quick path is one tap. The full colour range is one level
            // deeper, so three sliders never crowd the ordinary choice.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quick colours",
                        style = MaterialTheme.typography.titleSmall,
                        color = DhunColors.textPrimary,
                    )
                    Text(
                        text = "Tap one to apply it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DhunColors.textSecondary,
                    )
                }
                DhunTextButton(onClick = { customColour = true }) {
                    Text("More colours")
                }
            }
            // Fixed rows rather than a wrapping flow: seven picks fit two rows
            // on a phone, and a stable grid is easier to scan than a reflowing
            // one (and needs no experimental layout API).
            val picks = remember(component, tokens.isLight, defaultPreview) {
                buildList {
                    add(SwatchPick("Default", null, defaultPreview))
                    for (accent in DhunAccent.entries) {
                        add(
                            SwatchPick(
                                label = accent.label,
                                color = if (tokens.isLight) accent.light.accent else accent.dark.accent,
                            ),
                        )
                    }
                }
            }
            for (row in picks.chunked(SWATCHES_PER_ROW)) {
                Row(horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm)) {
                    for (pick in row) {
                        ColorSwatch(
                            label = pick.label,
                            color = pick.preview,
                            // Exact match on purpose: a colour restored from an
                            // older build is not one of these picks, and showing
                            // nothing selected is the honest reading of that.
                            selected = picked == pick.color,
                            onPick = { viewModel.setComponentColor(component, pick.color) },
                        )
                    }
                }
            }

            /*
             * Overrides for this surface only - not a second copy of the global
             * settings. They sit *after* the colour the user came here to pick,
             * they are disabled while their global is off, and their captions
             * name the global that suspends them, so the two switches at the top
             * of the screen stay the two decisions that govern the app.
             */
            Text(
                text = "Overrides for this surface",
                style = MaterialTheme.typography.titleSmall,
                color = DhunColors.textPrimary,
            )
            BackgroundSwitchRow(
                title = "Background blur here",
                subtitle = if (state.blurEnabled) {
                    "Only this surface. Turn Background blur off above to remove artwork everywhere."
                } else {
                    "Background blur is off above, so no surface shows artwork. This choice is remembered."
                },
                checked = prefs.blurEnabled,
                enabled = state.blurEnabled,
                onCheckedChange = { viewModel.setComponentBlurEnabled(component, it) },
            )
            BackgroundSwitchRow(
                title = "Take colour from artwork",
                subtitle = when {
                    !state.dynamicColors -> "Dynamic colours is off above, so every surface uses its own colour."
                    !prefs.followArtwork -> "Only this surface: the others still follow the cover."
                    else -> "This surface follows the playing cover."
                },
                checked = prefs.followArtwork,
                enabled = state.dynamicColors,
                onCheckedChange = { viewModel.setComponentFollowArtwork(component, it) },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DhunTextButton(onClick = { viewModel.resetComponentBackgrounds(component) }) {
                    Text("Restore defaults")
                }
                Spacer(modifier = Modifier.width(DhunSpacing.sm))
                DhunButton(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}

/**
 * The full colour range, one step away from the quick picks.
 *
 * The draft stays local until **Use colour**. A drag therefore repaints only
 * this preview; it does not enqueue a database write for every pointer frame.
 * Applying still calls the same per-component setter as every quick swatch.
 */
@Composable
private fun CustomColourEditor(
    initialColor: Color,
    onBack: () -> Unit,
    onUseColour: (Color) -> Unit,
) {
    var draft by remember(initialColor) { mutableStateOf(initialColor.toHsl()) }
    val preview = draft.toColor()
    val hueColors = remember { hueSliderColors() }

    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = DhunSpacing.dialogMaxWidth),
        verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DhunIconButton(
                onClick = onBack,
                modifier = Modifier.size(DhunSpacing.touchTarget),
                contentDescription = "Back to quick colours",
            ) {
                DhunIconView(
                    icon = DhunIcon.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(DhunSpacing.iconSize),
                    tint = DhunColors.textPrimary,
                )
            }
            Spacer(modifier = Modifier.width(DhunSpacing.sm))
            Text(
                text = "Custom colour",
                style = MaterialTheme.typography.titleLarge,
                color = DhunColors.textPrimary,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(DhunShapes.medium)
                .background(preview)
                .border(DhunSpacing.border, DhunColors.glassEdge, DhunShapes.medium)
                .semantics { contentDescription = "Selected colour preview" },
        )
        Text(
            text = "Move the sliders, then choose Use colour.",
            style = MaterialTheme.typography.bodySmall,
            color = DhunColors.textSecondary,
        )
        HslSlider(
            label = "Hue",
            value = draft.hue,
            valueRange = 0f..360f,
            valueDescription = "${draft.hue.roundToInt()} degrees",
            colors = hueColors,
            onValueChange = { draft = draft.copy(hue = it) },
        )
        HslSlider(
            label = "Saturation",
            value = draft.saturation,
            valueRange = 0f..1f,
            valueDescription = "${(draft.saturation * 100).roundToInt()} percent",
            colors = saturationSliderColors(draft),
            onValueChange = { draft = draft.copy(saturation = it) },
        )
        HslSlider(
            label = "Lightness",
            value = draft.lightness,
            valueRange = 0f..1f,
            valueDescription = "${(draft.lightness * 100).roundToInt()} percent",
            colors = lightnessSliderColors(draft),
            onValueChange = { draft = draft.copy(lightness = it) },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            DhunTextButton(onClick = onBack) { Text("Cancel") }
            Spacer(modifier = Modifier.width(DhunSpacing.sm))
            DhunButton(onClick = { onUseColour(preview) }) { Text("Use colour") }
        }
    }
}

/** An accessible slider with the colour ramp painted into its rounded track. */
@Composable
private fun HslSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueDescription: String,
    colors: List<Color>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = DhunColors.textPrimary,
        )
        DhunColourSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            valueRange = valueRange,
            trackColors = colors,
            contentDescription = label,
            stateDescription = valueDescription,
        )
    }
}

/** One offered colour: its label, the value stored (`null` = default), its swatch. */
private data class SwatchPick(
    val label: String,
    val color: Color?,
    val preview: Color = color ?: Color.Transparent,
)

/** Seven picks (default + six accents) read as two rows of four and three. */
private const val SWATCHES_PER_ROW = 4

/**
 * One colour pick.
 *
 * The quick path uses a small, named set. More colours accepts the full HSL
 * range; both paths store one colour and use the same existing luminance clamp
 * as the artwork palette, so custom colours cannot bypass readable surfaces.
 */
@Composable
private fun ColorSwatch(
    label: String,
    color: Color,
    selected: Boolean,
    onPick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .clip(DhunShapes.medium)
            .clickable(role = Role.RadioButton, onClick = onPick)
            .semantics { contentDescription = "$label colour" }
            .padding(vertical = DhunSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(DhunSpacing.xxl + DhunSpacing.xs)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) DhunSpacing.divider * 2 else DhunSpacing.border,
                    color = if (selected) DhunColors.accent else DhunColors.glassEdge,
                    shape = CircleShape,
                ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) DhunColors.textPrimary else DhunColors.textSecondary,
            maxLines = 1,
        )
    }
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
                        palette.base,
                        blend(palette.base, palette.mainGlow, 0.55f),
                        blend(palette.base, palette.companionGlow, 0.55f),
                    ),
                ),
            )
            .border(DhunSpacing.border, DhunColors.glassEdge, DhunShapes.medium),
    )
}

/**
 * Preview blend of two palette layers.
 *
 * The alpha is blended too, not forced to 1: the glow layers are deliberately
 * translucent (and floored, never opaque), and painting them solid in the
 * preview made a bright palette look more saturated here than it ever looks
 * behind the app.
 */
private fun blend(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * k,
        green = a.green + (b.green - a.green) * k,
        blue = a.blue + (b.blue - a.blue) * k,
        alpha = a.alpha + (b.alpha - a.alpha) * k,
    )
}
