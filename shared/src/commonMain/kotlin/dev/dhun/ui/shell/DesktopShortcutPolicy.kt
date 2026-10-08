package dev.dhun.ui.shell

/** Pure guard for the global Space play/pause shortcut. */
internal object DesktopShortcutPolicy {
    fun shouldTogglePlaybackOnSpace(
        isDesktop: Boolean,
        isKeyDown: Boolean,
        isSpace: Boolean,
        controlPressed: Boolean,
        textInputFocused: Boolean,
    ): Boolean =
        isDesktop && isKeyDown && isSpace && !controlPressed && !textInputFocused
}
