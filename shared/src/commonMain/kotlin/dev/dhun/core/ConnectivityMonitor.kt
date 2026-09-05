package dev.dhun.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Connectivity observation for the offline banner (Phase 14 error-taxonomy
 * item: "offline banner"). Each platform pushes its best available signal
 * into [isOnline]; the shared app shell renders an actionable banner while
 * it is false. Failure to determine state must report `true` (never flash
 * a false offline banner).
 */
interface ConnectivityMonitor {
    val isOnline: StateFlow<Boolean>
}

/** Hosts that cannot observe connectivity — banner simply never shows. */
object AlwaysOnlineConnectivityMonitor : ConnectivityMonitor {
    override val isOnline: StateFlow<Boolean> = MutableStateFlow(true)
}
