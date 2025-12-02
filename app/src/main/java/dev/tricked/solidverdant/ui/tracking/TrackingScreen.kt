package dev.tricked.solidverdant.ui.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.model.User
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Tracking screen displaying current time tracking state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    user: User?,
    uiState: TrackingUiState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onStartTracking: () -> Unit = {},
    onStopTracking: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Time Tracking")
                        user?.name?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Logout"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator()
                }

                uiState.error != null -> {
                    ErrorCard(error = uiState.error)
                }

                uiState.isTracking && uiState.currentTimeEntry != null -> {
                    TrackingCard(
                        timeEntry = uiState.currentTimeEntry,
                        elapsedSeconds = uiState.elapsedSeconds,
                        onStop = onStopTracking
                    )
                }

                else -> {
                    NotTrackingCard(onStart = onStartTracking)
                }
            }
        }
    }
}

/**
 * Card displayed when actively tracking time
 */
@Composable
private fun TrackingCard(
    timeEntry: TimeEntry,
    elapsedSeconds: Long,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Currently Tracking",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = formatElapsedTime(elapsedSeconds),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                if (!timeEntry.description.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = timeEntry.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Details card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Entry Details",
                    style = MaterialTheme.typography.titleMedium
                )

                HorizontalDivider()

                DetailRow("Started", formatTimestamp(timeEntry.start))
                DetailRow("Entry ID", timeEntry.id.take(8))
                DetailRow("Billable", if (timeEntry.billable) "Yes" else "No")

                if (timeEntry.tags.isNotEmpty()) {
                    DetailRow("Tags", timeEntry.tags.joinToString(", ") { it.name })
                }
            }
        }

        // Stop button
        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Stop"
            )
            Spacer(modifier = Modifier.padding(4.dp))
            Text("Stop Tracking")
        }
    }
}

/**
 * Card displayed when not tracking
 */
@Composable
private fun NotTrackingCard(onStart: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Not Tracking",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "No active time entry",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onStart) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Start"
                )
                Spacer(modifier = Modifier.padding(4.dp))
                Text("Start Tracking")
            }
        }
    }
}

/**
 * Error card
 */
@Composable
private fun ErrorCard(error: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/**
 * Detail row for displaying key-value pairs
 */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Format elapsed time as HH:MM:SS
 */
private fun formatElapsedTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, secs)
}

/**
 * Format timestamp for display
 */
private fun formatTimestamp(timestamp: String): String {
    return try {
        val zonedDateTime = ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME)
        zonedDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    } catch (e: Exception) {
        timestamp
    }
}
