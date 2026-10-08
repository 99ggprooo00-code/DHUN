package dev.dhun.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing

/**
 * DHUN's one-line text input.
 *
 * The app has no forms; it has one or two dialogs that ask for a name, and they
 * used Material 3's `OutlinedTextField` — 56dp of Material chrome (outline,
 * floating label, its own container colour) dropped into a 280–400dp glass
 * card. On the device that reads as an **oversized box** that does not belong to
 * the surface around it, and it was one of the two complaints about the Add to
 * playlist sheet.
 *
 * This is the same control in the app's own language: a rounded, elevated
 * `surfaceElevated` field with a hairline border, the accent as the caret, a
 * tertiary placeholder, and nothing else. It wraps its own content height
 * ([DhunSpacing.smPlus] vertical padding around one line), so it can never
 * reserve more room than the text it holds, and it takes the whole width its
 * caller gives it.
 *
 * `isError` swaps the hairline for [DhunColors.borderError]; the message itself
 * stays with the caller (each dialog words its own validation), which is why
 * this component has no error slot.
 */
@Composable
fun DhunTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    /** Invoked by the keyboard's Done/Go action; null hides the affordance. */
    onSubmit: (() -> Unit)? = null,
) {
    val focusRegistry = LocalTextInputFocusRegistry.current
    val focusToken = remember { Any() }
    DisposableEffect(focusRegistry, focusToken) {
        onDispose { focusRegistry?.setFocused(focusToken, false) }
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = DhunColors.textPrimary),
        cursorBrush = SolidColor(DhunColors.accent),
        keyboardOptions = KeyboardOptions(
            imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke() }),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusRegistry?.setFocused(focusToken, it.isFocused) },
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(DhunShapes.medium)
                    .background(DhunColors.surfaceElevated)
                    .border(
                        BorderStroke(
                            DhunSpacing.border,
                            if (isError) DhunColors.borderError else DhunColors.borderStrong,
                        ),
                        DhunShapes.medium,
                    )
                    .padding(horizontal = DhunSpacing.md, vertical = DhunSpacing.smPlus),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DhunColors.textTertiary,
                    )
                }
                innerTextField()
            }
        },
    )
}
