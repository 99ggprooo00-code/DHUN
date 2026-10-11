package dev.dhun.player

import dev.dhun.core.RepeatMode
import dev.dhun.data.SettingsKeys
import dev.dhun.data.SettingsRepository

/** New queues are independent unless the user explicitly retains transport modes. */
suspend fun resetModesForNewQueue(player: DhunPlayer, settings: SettingsRepository?) {
    if (settings?.getBoolean(SettingsKeys.PERSIST_SHUFFLE_REPEAT, true) == false) {
        player.setRepeatMode(RepeatMode.OFF)
        player.setShuffle(false)
    }
}
