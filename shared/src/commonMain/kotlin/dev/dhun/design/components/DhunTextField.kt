package dev.dhun.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.input.ImeAction
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.VisualPart

/**
 * DHUN's compact one-line input: a tonal translucent field, hairline edge,
 * accent caret and tertiary placeholder. Optional leading/trailing actions and
 * keyboard hooks keep search in the same component family as playlist names.
 */
@Composable
fun DhunTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    /** Invoked by the keyboard's Done/Search action; null hides the affordance. */
    onSubmit: (() -> Unit)? = null,
    shape: Shape = DhunShapes.medium,
    imeAction: ImeAction = ImeAction.Done,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    /** Optional physical-key handling; return true to consume the event. */
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
) {
    val focusRegistry = LocalTextInputFocusRegistry.current
    val focusToken = remember { Any() }
    DisposableEffect(focusRegistry, focusToken) {
        onDispose { focusRegistry?.setFocused(focusToken, false) }
    }
    val keyHandler = onKeyEvent
    val hasChrome = leadingContent != null || trailingContent != null

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = DhunColors.textPrimary),
        cursorBrush = SolidColor(DhunColors.accent),
        keyboardOptions = KeyboardOptions(
            imeAction = if (onSubmit != null) imeAction else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(
            onDone = { if (imeAction == ImeAction.Done) onSubmit?.invoke() },
            onSearch = { if (imeAction == ImeAction.Search) onSubmit?.invoke() },
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (hasChrome) DhunSpacing.touchTarget + DhunSpacing.sm else DhunSpacing.zero)
            .onFocusChanged { focusRegistry?.setFocused(focusToken, it.isFocused) }
            .then(
                if (keyHandler == null) {
                    Modifier
                } else {
                    Modifier.onKeyEvent { event -> keyHandler(event) }
                },
            ),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(contentRowFill(VisualPart.INPUTS))
                    .border(
                        BorderStroke(
                            DhunSpacing.border,
                            if (isError) DhunColors.borderError else DhunColors.borderStrong,
                        ),
                        shape,
                    )
                    .padding(
                        horizontal = DhunSpacing.md,
                        vertical = if (trailingContent != null) DhunSpacing.xs else DhunSpacing.smPlus,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leadingContent?.invoke()
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = DhunColors.textTertiary,
                            )
                        }
                        innerTextField()
                    }
                    trailingContent?.invoke()
                }
            }
        },
    )
}
