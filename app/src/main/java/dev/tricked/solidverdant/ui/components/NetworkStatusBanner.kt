package dev.tricked.solidverdant.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun NetworkAwareContent(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isOnline by produceState(initialValue = context.hasValidatedInternet(), context) {
        context.connectivityFlow().collect { reportedOnline ->
            if (reportedOnline) {
                value = true
            } else {
                // Network hand-offs and process thaw briefly report no active network.
                // Confirm the outage before showing a persistent offline banner.
                delay(OFFLINE_CONFIRMATION_DELAY_MS)
                value = context.hasValidatedInternet()
            }
        }
    }
    var hasBeenOffline by remember { mutableStateOf(!isOnline) }
    var showRestored by remember { mutableStateOf(false) }

    LaunchedEffect(isOnline) {
        if (!isOnline) {
            hasBeenOffline = true
            showRestored = false
        } else if (hasBeenOffline) {
            showRestored = true
            delay(RESTORED_MESSAGE_DURATION_MS)
            showRestored = false
            hasBeenOffline = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(
            visible = !isOnline || showRestored,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Surface(
                color = if (isOnline) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                contentColor = if (isOnline) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 6.dp,
                modifier = Modifier.wrapContentWidth()
            ) {
                Text(
                    text = stringResource(
                        if (isOnline) R.string.network_restored else R.string.network_unavailable
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                )
            }
        }
    }
}

private fun Context.connectivityFlow() = callbackFlow {
    val connectivityManager =
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = reportStatus()
        override fun onLost(network: Network) = reportStatus()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            reportStatus()

        private fun reportStatus() {
            trySend(hasValidatedInternet())
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
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

private const val RESTORED_MESSAGE_DURATION_MS = 3_000L
private const val OFFLINE_CONFIRMATION_DELAY_MS = 1_500L
