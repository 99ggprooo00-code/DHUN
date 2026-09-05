package dev.dhun.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.NetworkInterface

/**
 * Desktop connectivity signal for the offline banner. The JVM has no
 * cross-OS network-event API without new dependencies, so this is a
 * lightweight poll (default every 5s, IO dispatcher): online = at least one
 * non-loopback, up interface with addresses. Indeterminate ⇒ true (never
 * flash a false banner).
 */
class DesktopConnectivityMonitor(
    scope: CoroutineScope,
    private val pollIntervalMs: Long = 5_000,
) : ConnectivityMonitor {

    private val state = MutableStateFlow(true) // optimistic until first probe
    override val isOnline: StateFlow<Boolean> = state

    init {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                state.value = probe()
                delay(pollIntervalMs)
            }
        }
    }

    private fun probe(): Boolean = try {
        NetworkInterface.getNetworkInterfaces()
            ?.asSequence()
            ?.any { !it.isLoopback && it.isUp && it.inetAddresses.toList().isNotEmpty() }
            ?: false
    } catch (_: Exception) {
        true // cannot determine — do not show a false offline banner
    }
}
