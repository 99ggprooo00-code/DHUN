package dev.dhun.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.dhun.design.VisualPart
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing

/**
 * DHUN button family — all states (normal / pressed / disabled / loading)
 * are exercised in the catalogue. Wrapper around Material3 so the app can
 * swap internals without touching call sites.
 */

@Composable
fun DhunButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = DhunSpacing.lg, vertical = DhunSpacing.sm),
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !loading,
        shape = DhunShapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = DhunColors.accent,
            contentColor = DhunColors.onAccent,
            disabledContainerColor = DhunColors.surfaceElevated,
            disabledContentColor = DhunColors.textDisabled,
        ),
        contentPadding = contentPadding,
        content = {
            if (loading) {
                // DHUN spinner, not Material's solid ring (see DhunSpinner).
                DhunSpinner(size = 16.dp, strokeWidth = 2.dp, color = DhunColors.onAccent)
            } else {
                content()
            }
        },
    )
}

@Composable
fun DhunTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !loading,
        shape = DhunShapes.button,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = DhunColors.accentContainer,
            contentColor = DhunColors.onAccentContainer,
        ),
        content = {
            if (loading) {
                DhunSpinner(size = 16.dp, strokeWidth = 2.dp, color = DhunColors.onAccentContainer)
            } else {
                content()
            }
        },
    )
}

/**
 * The secondary action: a frosted pill, not Material's hollow outline.
 *
 * `OutlinedButton` with a DHUN border colour is still an *outlined button* —
 * an empty pill with a 1dp stroke, which on a glass page reads as the one
 * unstyled control in the row. It carries the chip's glass body instead
 * (translucent fill, lifted top, sheen, soft edge), so "Rename"/"Shuffle"
 * belong to the surface they sit on.
 */
@Composable
fun DhunOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val body = if (enabled) {
        modifier.clip(DhunShapes.button).dhunGlassBody(contentRowFill(VisualPart.CHIPS), DhunShapes.button)
    } else {
        modifier
    }
    OutlinedButton(
        onClick = onClick,
        modifier = body,
        enabled = enabled,
        shape = DhunShapes.button,
        border = BorderStroke(DhunSpacing.border, DhunColors.glassEdge),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = DhunColors.textPrimary,
            disabledContentColor = DhunColors.textDisabled,
        ),
        content = content,
    )
}

@Composable
fun DhunTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = DhunShapes.button,
        colors = ButtonDefaults.textButtonColors(
            contentColor = DhunColors.accent,
        ),
        content = content,
    )
}

@Composable
fun DhunIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.then(
            if (contentDescription == null) Modifier else Modifier.semantics {
                this.contentDescription = contentDescription
            },
        ),
        enabled = enabled,
        content = content,
    )
}
