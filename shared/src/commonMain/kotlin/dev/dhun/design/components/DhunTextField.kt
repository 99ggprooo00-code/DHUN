package dev.dhun.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunShapes
import dev.dhun.design.DhunSpacing
import dev.dhun.design.VisualPart

/**
 * DHUN's text input.
 *
 * The app has no forms; it has a handful of places that ask for a name, a
 * search query or a document to paste. They used Material 3's
 * `OutlinedTextField` — 56dp of Material chrome (outline, floating label, its
 * own container colour) dropped into a 280–400dp glass card. On the device that
 * reads as an **oversized box** that does not belong to the surface around it.
 *
 * This is the same control in the app's own language: a rounded, translucent
 * tonal field with a hairline edge, the accent as the caret, a tertiary
 * placeholder, and nothing else. It wraps its own content height, so it can
 * never reserve more room than the text it holds, and it takes the whole width
 * its caller gives it.
 *
 * It covers every input the app actually has, so no screen needs to fall back
 * to Material's chrome:
 *  - [leading] / [trailing] slots (search icon, clear button),
 *  - [label] above the field (replaces M3's floating label),
 *  - [singleLine] = false with [minLines]/[maxLines] for pasted documents,
 *  - [imeAction] + [onSubmit] for keyboard submission,
 *  - [shape] for the pill-shaped search field.
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
    /** Invoked by the keyboard's Done/Search action; null hides the affordance. */
    onSubmit: (() -> Unit)? = null,
    label: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    imeAction: ImeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Default,
    shape: Shape = DhunShapes.medium,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val focusRegistry = LocalTextInputFocusRegistry.current
    val focusToken = remember { Any() }
    DisposableEffect(focusRegistry, focusToken) {
        onDispose { focusRegistry?.setFocused(focusToken, false) }
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = DhunColors.textPrimary),
        cursorBrush = SolidColor(DhunColors.accent),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onDone = { onSubmit?.invoke() },
            onSearch = { onSubmit?.invoke() },
            onGo = { onSubmit?.invoke() },
        ),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusRegistry?.setFocused(focusToken, it.isFocused) },
        decorationBox = { innerTextField ->
            Column(modifier = Modifier.fillMaxWidth()) {
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = DhunColors.textTertiary,
                        modifier = Modifier.padding(start = DhunSpacing.xs, bottom = DhunSpacing.xs),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        // The same quiet glass as a row, so an input belongs to
                        // the panel around it instead of sitting on top of it.
                        .background(contentRowFill(VisualPart.INPUTS))
                        .border(
                            BorderStroke(
                                DhunSpacing.border,
                                if (isError) DhunColors.borderError else DhunColors.borderStrong,
                            ),
                            shape,
                        )
                        .padding(horizontal = DhunSpacing.md, vertical = DhunSpacing.smPlus),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
                ) {
                    leading?.invoke()
                    Box(
                        modifier = if (singleLine) {
                            Modifier.weight(1f)
                        } else {
                            Modifier.weight(1f).heightIn(min = DhunSpacing.touchTarget)
                        },
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
                    trailing?.invoke()
                }
            }
        },
    )
}
