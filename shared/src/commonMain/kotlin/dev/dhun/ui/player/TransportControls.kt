package dev.dhun.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import dev.dhun.core.PlaybackState
import dev.dhun.design.DhunAnimations
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import kotlinx.coroutines.withTimeoutOrNull

/** Tap/keyboard/accessibility = skip; pointer hold = seek, with paired cancellation cleanup. */
@Composable
internal fun HoldTapTransportButton(
    forward: Boolean,
    icon: DhunIcon,
    contentDescription: String,
    onTap: () -> Unit,
    onHold: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: Dp = DhunSpacing.iconSize,
) {
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnHold by rememberUpdatedState(onHold)
    val currentOnRelease by rememberUpdatedState(onRelease)
    var pointerPressed by remember { mutableStateOf(false) }
    var keyboardPressed by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pointerPressed || keyboardPressed) 1.15f else 1f,
        animationSpec = DhunAnimations.fastTween(),
        label = "holdScale",
    )
    Box(
        modifier = modifier
            .size(DhunSpacing.touchTarget)
            .clip(DhunShapes.full)
            .border(DhunSpacing.border, if (focused) DhunColors.accent else Color.Transparent, DhunShapes.full)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.contentDescription = contentDescription
                if (enabled) {
                    onClick(label = contentDescription) { currentOnTap(); true }
                } else {
                    disabled()
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) keyboardPressed = false
            }
            .onKeyEvent { event ->
                if (!enabled || !isTransportActivationKey(event.key) ||
                    event.isCtrlPressed || event.isAltPressed || event.isMetaPressed
                ) {
                    false
                } else {
                    when (event.type) {
                        KeyEventType.KeyDown -> keyboardPressed = true
                        KeyEventType.KeyUp -> {
                            if (keyboardPressed) currentOnTap()
                            keyboardPressed = false
                        }
                        else -> Unit
                    }
                    true // Consume key-up too; the window's Space shortcut must not also run.
                }
            }
            .focusable(enabled = enabled)
            .pointerInput(forward, enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown().consume()
                    // Preserve the tested matching pair for this exact press,
                    // including disposal when the caller's track key changes.
                    val press = TransportPress(currentOnTap, currentOnHold, currentOnRelease)
                    pointerPressed = true
                    try {
                        val released = withTimeoutOrNull(HOLD_DELAY_MS) {
                            waitForUpOrCancellation()?.also { it.consume() } != null
                        }
                        press.initialWaitFinished(released)
                        if (press.holding) waitForUpOrCancellation()?.consume()
                    } finally {
                        pointerPressed = false
                        press.finish()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        DhunIconView(
            icon = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize)
                .graphicsLayer { scaleX = scale; scaleY = scale },
            tint = if (enabled) DhunColors.textPrimary else DhunColors.textTertiary,
        )
    }
}

internal fun isTransportActivationKey(key: Key): Boolean =
    key == Key.Enter || key == Key.NumPadEnter || key == Key.Spacebar || key == Key.DirectionCenter

internal fun playbackActionLabel(state: PlaybackState): String = when (state) {
    is PlaybackState.Error -> "Retry playback"
    is PlaybackState.Playing -> "Pause"
    else -> "Play"
}

internal fun playbackActionIcon(state: PlaybackState): DhunIcon = when (state) {
    is PlaybackState.Error -> DhunIcon.Refresh
    is PlaybackState.Playing -> DhunIcon.Pause
    else -> DhunIcon.Play
}

internal fun playbackBusyLabel(state: PlaybackState): String? = when (state) {
    is PlaybackState.Resolving -> "Resolving…"
    is PlaybackState.Buffering -> "Buffering…"
    is PlaybackState.Recovering -> "Reconnecting…"
    else -> null
}

/**
 * Immersive main-transport geometry: previous/next keep their 48dp touch
 * targets, the play action owns the 52dp target, and the bare icons grow on
 * roomy widths. Nothing shrinks below the touch floor — at exceptionally
 * narrow widths the row scrolls instead (see [minimumWidth]).
 */
internal data class PlayerTransportMetrics(
    val horizontalPadding: Dp,
    /** Play/pause icon size (the bare glyph — no disc). */
    val playSize: Dp,
    /** Previous/next icon size. */
    val skipSize: Dp,
    val minimumWidth: Dp,
)

internal fun playerTransportMetrics(availableWidth: Dp): PlayerTransportMetrics {
    val roomy = availableWidth >= DhunSpacing.playerTransportMaxWidth
    val padding = if (roomy) DhunSpacing.xxl else DhunSpacing.xs
    val playSize = if (roomy) DhunSpacing.huge else DhunSpacing.iconSizeLg
    val skipSize = if (roomy) DhunSpacing.iconSizeLg else DhunSpacing.iconSize
    val minimumWidth = DhunSpacing.touchTarget * 2 + DhunSpacing.transportTarget + padding * 2
    return PlayerTransportMetrics(padding, playSize, skipSize, minimumWidth)
}

private const val HOLD_DELAY_MS = 350L
