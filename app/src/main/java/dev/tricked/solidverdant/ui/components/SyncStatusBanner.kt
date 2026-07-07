package dev.tricked.solidverdant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.sync.SyncStatus
import dev.tricked.solidverdant.ui.theme.Dimens
import kotlinx.coroutines.delay

/**
 * The single ambient status affordance for the whole app. It folds the two
 * former stacked overlays (connectivity + sync error) into one banner so a
 * failure or an offline device surfaces exactly once, with a retry, using kit
 * tokens. The healthy state renders nothing.
 *
 * Only one status wins at a time (offline outranks a sync error, because a
 * retry is pointless with no network). The detailed per-entry recovery still
 * lives in the Track screen's Sync center; this banner is the at-a-glance
 * global surface.
 */
@Composable
fun AppStatusOverlay(
    syncStatus: SyncStatus,
    onRetrySync: () -> Unit,
    content: @Composable () -> Unit,
) {
    val isOnline = rememberIsOnline()
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

    val status: AppStatus? = when {
        !isOnline -> AppStatus.Offline
        syncStatus is SyncStatus.Error -> AppStatus.SyncError
        showRestored -> AppStatus.Restored
        else -> null
    }

    // Retain the last shown status so the banner keeps its content while it
    // slides out (status is null during the exit animation).
    var visibleStatus by remember { mutableStateOf<AppStatus?>(null) }
    LaunchedEffect(status) { if (status != null) visibleStatus = status }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(
            visible = status != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(Dimens.Space16),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            visibleStatus?.let { StatusBanner(status = it, onRetry = onRetrySync) }
        }
    }
}

private enum class AppStatus { Offline, SyncError, Restored }

@Composable
private fun StatusBanner(status: AppStatus, onRetry: () -> Unit) {
    val container: Color
    val onContainer: Color
    val messageRes: Int
    val showRetry: Boolean
    when (status) {
        AppStatus.Offline -> {
            container = MaterialTheme.colorScheme.errorContainer
            onContainer = MaterialTheme.colorScheme.onErrorContainer
            messageRes = R.string.network_unavailable
            showRetry = false
        }
        AppStatus.SyncError -> {
            container = MaterialTheme.colorScheme.errorContainer
            onContainer = MaterialTheme.colorScheme.onErrorContainer
            messageRes = R.string.sync_error
            showRetry = true
        }
        AppStatus.Restored -> {
            container = MaterialTheme.colorScheme.primaryContainer
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer
            messageRes = R.string.network_restored
            showRetry = false
        }
    }

    Surface(
        color = container,
        contentColor = onContainer,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 6.dp,
        modifier = Modifier.wrapContentWidth(),
    ) {
        Row(
            modifier = Modifier.padding(
                start = Dimens.Space16,
                end = if (showRetry) Dimens.Space8 else Dimens.Space16,
                top = Dimens.Space4,
                bottom = Dimens.Space4,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
        ) {
            Text(
                text = stringResource(messageRes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = Dimens.Space8),
            )
            if (showRetry) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

private const val RESTORED_MESSAGE_DURATION_MS = 3_000L
