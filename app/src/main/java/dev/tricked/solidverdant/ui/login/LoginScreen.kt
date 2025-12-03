package dev.tricked.solidverdant.ui.login

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.ui.auth.AuthUiState
import dev.tricked.solidverdant.ui.auth.OAuthConfigState
import dev.tricked.solidverdant.ui.config.ConfigScreen

/**
 * Login screen with OAuth flow initiation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    uiState: AuthUiState,
    configState: OAuthConfigState,
    onLoginClick: () -> Unit,
    onConfigSave: (String, String) -> Unit,
    onConfigReset: () -> Unit,
    onAuthUrlReady: (String) -> Unit,
    onClearAuthUrl: () -> Unit,
    onClearError: () -> Unit
) {
    val context = LocalContext.current
    var showConfigDialog by remember { mutableStateOf(false) }

    // Launch auth URL when available
    LaunchedEffect(uiState.authUrl) {
        uiState.authUrl?.let { url ->
            launchCustomTab(context, url)
            onClearAuthUrl()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.solidtime_login)) },
                actions = {
                    IconButton(onClick = { showConfigDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings)
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.time_tracking_client),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = onLoginClick,
                    enabled = !uiState.isLoading
                ) {
                    Text(stringResource(R.string.login_with_oauth2))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Display error if any
            uiState.error?.let { error ->
                ErrorCard(
                    error = error,
                    onDismiss = onClearError
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Display current configuration
            Text(
                text = stringResource(R.string.endpoint_label, configState.endpoint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Show config dialog
    if (showConfigDialog) {
        ConfigScreen(
            configState = configState,
            onSave = onConfigSave,
            onReset = onConfigReset,
            onDismiss = { showConfigDialog = false }
        )
    }
}

/**
 * Error card to display error messages
 */
@Composable
private fun ErrorCard(
    error: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.error),
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
 * Launch Custom Tab for OAuth flow
 */
private fun launchCustomTab(context: Context, url: String) {
    val customTabsIntent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
    customTabsIntent.launchUrl(context, android.net.Uri.parse(url))
}
