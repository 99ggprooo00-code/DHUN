package dev.dhun.design.components

import kotlin.test.Test
import kotlin.test.assertEquals

class DhunRefreshPolicyTest {
    @Test
    fun indicatorIsHiddenAtRestAndRevealsGradually() {
        assertEquals(0f, DhunRefreshPolicy.reveal(0f, isRefreshing = false))
        assertEquals(0.5f, DhunRefreshPolicy.reveal(DhunRefreshPolicy.FULL_ALPHA_AT / 2f, false))
        assertEquals(1f, DhunRefreshPolicy.reveal(DhunRefreshPolicy.FULL_ALPHA_AT, false))
        assertEquals(1f, DhunRefreshPolicy.reveal(2f, false))
        assertEquals(0f, DhunRefreshPolicy.reveal(-1f, false))
    }

    @Test
    fun activeRefreshKeepsTheIndicatorVisibleAfterTheFingerLeaves() {
        assertEquals(1f, DhunRefreshPolicy.reveal(0f, isRefreshing = true))
        assertEquals(1f, DhunRefreshPolicy.reveal(0.2f, isRefreshing = true))
    }

    @Test
    fun pullArcIsBoundedToOneGesture() {
        assertEquals(0f, DhunRefreshPolicy.sweepFraction(-0.2f))
        assertEquals(0.4f, DhunRefreshPolicy.sweepFraction(0.4f))
        assertEquals(1f, DhunRefreshPolicy.sweepFraction(1.8f))
    }
}
