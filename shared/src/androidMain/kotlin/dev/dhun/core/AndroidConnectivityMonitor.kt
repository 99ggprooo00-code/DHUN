package dev.dhun.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android connectivity signal for the offline banner: default-network
 * callback (API 24+, minSdk 26) plus a validated initial read of the
 * active network. NET_CAPABILITY_INTERNET present ⇒ online.
 */
class AndroidConnectivityMonitor(context: Context) : ConnectivityMonitor {

    private val cm =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val state = MutableStateFlow(currentlyOnline())

    override val isOnline: StateFlow<Boolean> = state

    init {
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                state.value = true
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                state.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }

            override fun onLost(network: Network) {
                state.value = currentlyOnline() // another network may still be up
            }
        })
    }

    private fun currentlyOnline(): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
