package dev.dhun.ui.search

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

object SearchInputPolicy {
    fun shouldSubmitOnKeyEvent(event: KeyEvent): Boolean {
        return event.key == Key.Enter && event.type == KeyEventType.KeyDown
    }
}
