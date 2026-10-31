package dev.dhun.presentation.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerLabelTest {
    @Test
    fun sleepChipChangesOnlyWhenTheDisplayedMinuteChanges() {
        val halfHour = 30 * 60 * 1_000L
        assertEquals("Sleep · 30m", sleepTimerChipLabel(halfHour))
        assertEquals("Sleep · 30m", sleepTimerChipLabel(halfHour - 1L))
        assertEquals("Sleep · 29m", sleepTimerChipLabel(halfHour - 60_000L))
        assertEquals("Sleep · 1m", sleepTimerChipLabel(60_000L))
        assertEquals("Sleep · 1m", sleepTimerChipLabel(1L))
    }
}
