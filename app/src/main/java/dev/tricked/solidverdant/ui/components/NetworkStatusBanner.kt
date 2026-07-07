package dev.tricked.solidverdant.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Observes validated-internet connectivity and returns the current online state.
 *
 * Network hand-offs and process thaw briefly report no active network, so a
 * reported loss is re-confirmed after [OFFLINE_CONFIRMATION_DELAY_MS] before the
 * device is treated as offline.
 *
 * The connectivity banner itself now lives in the single [AppStatusOverlay], so
 * this only exposes the raw state and no longer draws a second stacked surface.
 */
@Composable
fun rememberIsOnline(): Boolean {
    val context = LocalContext.current
    val isOnline by produceState(initialValue = context.hasValidatedInternet(), context) {
        context.connectivityFlow().collectLatest { reportedOnline ->
            if (reportedOnline) {
                value = true
            } else {
                delay(OFFLINE_CONFIRMATION_DELAY_MS)
                value = context.hasValidatedInternet()
            }
        }
    }
    return isOnline
}

private fun Context.connectivityFlow() = callbackFlow {
    val connectivityManager =
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // CapabilitiesChanged supplies the authoritative state immediately afterwards.
        }

        override fun onLost(network: Network) {
            trySend(false)
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            trySend(capabilities.hasValidatedInternet())
        }
    }

    trySend(hasValidatedInternet())
    connectivityManager.registerDefaultNetworkCallback(callback)
    awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()

private fun Context.hasValidatedInternet(): Boolean {
    val connectivityManager =
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasValidatedInternet()
}

private fun NetworkCapabilities.hasValidatedInternet(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

private const val OFFLINE_CONFIRMATION_DELAY_MS = 1_500L
