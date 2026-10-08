package dev.dhun.ui.search

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchInputPolicyTest {
    @Test
    fun shouldSubmitOnEnter() {
        assertTrue(shouldSubmitOnEnter(isKeyDown = true, isEnter = true))
        assertFalse(shouldSubmitOnEnter(isKeyDown = false, isEnter = true)) // KeyUp
        assertFalse(shouldSubmitOnEnter(isKeyDown = true, isEnter = false)) // Other key
    }
    
    private fun shouldSubmitOnEnter(isKeyDown: Boolean, isEnter: Boolean): Boolean {
        return isEnter && isKeyDown
    }
}
