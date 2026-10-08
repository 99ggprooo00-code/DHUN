package dev.dhun.design.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Tracks all focused editable controls so app-wide shortcuts do not steal text. */
internal class TextInputFocusRegistry {
    private val focusedInputs = mutableSetOf<Any>()

    var hasFocusedTextInput by mutableStateOf(false)
        private set

    fun setFocused(token: Any, focused: Boolean) {
        if (focused) focusedInputs.add(token) else focusedInputs.remove(token)
        hasFocusedTextInput = focusedInputs.isNotEmpty()
    }
}

internal val LocalTextInputFocusRegistry =
    staticCompositionLocalOf<TextInputFocusRegistry?> { null }
