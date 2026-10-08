package dev.dhun.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android connectivity signal for the offline banner: default-network
 * callback (API 24+, which is also minSdk — do not call this below 24)
 * plus an initial read of the active network.
 * A network is online only when it has both INTERNET and VALIDATED; a Wi-Fi
 * link or captive portal alone must not hide the offline notice.
 */
class AndroidConnectivityMonitor(context: Context) : ConnectivityMonitor {

    private val cm =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val state = MutableStateFlow(currentlyOnline())

    override val isOnline: StateFlow<Boolean> = state

    init {
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Capabilities can arrive just after onAvailable. Preserve the
                // last signal if Android has not published them yet rather
                // than flashing an unverified "online" state.
                onlineFor(network)?.let { state.value = it }
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                state.value = isInternetValidated(
                    hasInternetCapability = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    hasValidatedCapability = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                )
            }

            override fun onLost(network: Network) {
                state.value = currentlyOnline() // another default network may still be up
            }
        })
    }

    private fun currentlyOnline(): Boolean {
        val network = cm.activeNetwork ?: return false
        // An active network whose capabilities are temporarily unavailable is
        // indeterminate; follow the monitor contract and avoid a false alarm.
        return onlineFor(network) ?: true
    }

    private fun onlineFor(network: Network): Boolean? {
        val caps = cm.getNetworkCapabilities(network) ?: return null
        return isInternetValidated(
            hasInternetCapability = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            hasValidatedCapability = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        )
    }
}
