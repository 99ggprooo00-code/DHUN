package dev.dhun.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = DhunColors.onAccent,
                )
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
            if (loading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            else content()
        },
    )
}

@Composable
fun DhunOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = DhunShapes.button,
        border = BorderStroke(DhunSpacing.border, DhunColors.borderStrong),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = DhunColors.textPrimary,
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
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}
