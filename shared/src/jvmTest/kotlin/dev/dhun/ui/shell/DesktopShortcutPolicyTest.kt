package dev.dhun.ui.shell

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopShortcutPolicyTest {

    @Test
    fun `Space toggles playback only for an unmodified key down outside text inputs`() {
        assertTrue(
            DesktopShortcutPolicy.shouldTogglePlaybackOnSpace(
                isDesktop = true,
                isKeyDown = true,
                isSpace = true,
                controlPressed = false,
                textInputFocused = false,
            ),
        )

        val suppressedCases = listOf(
            // Preserve mobile behavior and keyboard typing/selection.
            listOf(false, true, true, false, false),
            listOf(true, false, true, false, false),
            listOf(true, true, false, false, false),
            listOf(true, true, true, true, false),
            listOf(true, true, true, false, true),
        )
        suppressedCases.forEach { (desktop, keyDown, space, control, textFocus) ->
            assertFalse(
                DesktopShortcutPolicy.shouldTogglePlaybackOnSpace(
                    isDesktop = desktop,
                    isKeyDown = keyDown,
                    isSpace = space,
                    controlPressed = control,
                    textInputFocused = textFocus,
                ),
            )
        }
    }
}
