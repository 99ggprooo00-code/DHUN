package dev.dhun.ui.player

/**
 * Owns one pointer press, including the matching hold-release callback. Compose's
 * waitForUpOrCancellation returns null on cancellation; the surrounding timeout
 * also returns null on expiry. Convert the INNER result to Boolean first so
 * true = release, false = cancellation, null = hold deadline (not a tap).
 */
internal class TransportPress(
    private val onTap: () -> Unit,
    private val onHold: () -> Unit,
    private val onRelease: () -> Unit,
) {
    var holding: Boolean = false
        private set

    fun initialWaitFinished(releasedBeforeDeadline: Boolean?) {
        when (releasedBeforeDeadline) {
            true -> onTap()
            false -> Unit
            null -> {
                // Set before invoking the callback: cleanup must still pair
                // with onHold if it starts work and then throws.
                holding = true
                onHold()
            }
        }
    }

    /** Called from the pointer handler's finally block, including disposal/cancellation. */
    fun finish() {
        if (holding) {
            holding = false
            onRelease()
        }
    }
}
