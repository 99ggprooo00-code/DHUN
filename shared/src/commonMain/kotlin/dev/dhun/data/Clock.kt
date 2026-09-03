package dev.dhun.data

/** Wall-clock source, injectable so repository tests are deterministic. */
fun interface EpochClock {
    fun nowMs(): Long

    companion object {
        val System: EpochClock = EpochClock { currentEpochMillis() }
    }
}

internal expect fun currentEpochMillis(): Long
