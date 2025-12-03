package dev.tricked.solidverdant.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.ui.auth.OAuthConfigState

/**
 * Configuration dialog for OAuth settings
 */
@Composable
fun ConfigScreen(
    configState: OAuthConfigState,
    onSave: (String, String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var endpoint by remember { mutableStateOf(configState.endpoint) }
    var clientId by remember { mutableStateOf(configState.clientId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.oauth_configuration)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it.trim() },
                    label = { Text(stringResource(R.string.server_endpoint)) },
                    placeholder = { Text(stringResource(R.string.server_endpoint_placeholder)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it.trim() },
                    label = { Text(stringResource(R.string.client_id)) },
                    singleLine = true
                )

                // Reset to defaults button
                TextButton(
                    onClick = {
                        onReset()
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.reset_to_defaults))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(endpoint.removeSuffix("/"), clientId)
                    onDismiss()
                },
                enabled = endpoint.isNotBlank() && clientId.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
