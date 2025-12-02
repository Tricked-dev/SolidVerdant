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
import androidx.compose.ui.unit.dp
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
        title = { Text("OAuth Configuration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it.trim() },
                    label = { Text("Server Endpoint") },
                    placeholder = { Text("https://app.solidtime.io") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it.trim() },
                    label = { Text("Client ID") },
                    singleLine = true
                )

                // Reset to defaults button
                TextButton(
                    onClick = {
                        onReset()
                        onDismiss()
                    }
                ) {
                    Text("Reset to Defaults")
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
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
