/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.privacy

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.tricked.solidverdant.R

/**
 * Privacy & data-management screen (roadmap #48). Explains what the app stores locally, what leaves
 * the device, how credentials are protected, and which optional permissions do what — then offers
 * the three data controls: export diagnostics (#49), clear the re-syncable cache, and log out /
 * revoke the local session. Destructive actions confirm first. Status is communicated with text and
 * icons, never color alone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit = {}, onLogout: () -> Unit = {}) {
    val viewModel: PrivacyViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var showClearDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.privacy_navigate_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(
                icon = Icons.Outlined.Storage,
                title = stringResource(R.string.privacy_stored_title),
            ) {
                Body(stringResource(R.string.privacy_stored_solidtime))
                Body(stringResource(R.string.privacy_stored_local))
                Body(stringResource(R.string.privacy_stored_tokens))
                Body(stringResource(R.string.privacy_source_legend))
            }

            SectionCard(
                icon = Icons.Outlined.CloudUpload,
                title = stringResource(R.string.privacy_sent_title),
            ) {
                Body(stringResource(R.string.privacy_sent_endpoint_label))
                Text(
                    text = state.serverHost.ifBlank { stringResource(R.string.privacy_sent_endpoint_unknown) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Body(stringResource(R.string.privacy_sent_body))
            }

            SectionCard(
                icon = Icons.Outlined.Lock,
                title = stringResource(R.string.privacy_tokens_title),
            ) {
                Body(stringResource(R.string.privacy_tokens_body))
            }

            SectionCard(
                icon = Icons.Outlined.Notifications,
                title = stringResource(R.string.privacy_permissions_title),
            ) {
                Body(stringResource(R.string.privacy_permissions_notifications))
                Body(stringResource(R.string.privacy_permissions_calendar))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            .setData(android.net.Uri.parse("package:${context.packageName}")),
                                    )
                                }
                            }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.privacy_permissions_open_settings))
                }
            }

            SectionCard(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.privacy_storage_title),
            ) {
                if (state.computingStorage) {
                    Body(stringResource(R.string.privacy_storage_computing))
                } else {
                    StorageRow(
                        label = stringResource(R.string.privacy_storage_database),
                        value = ByteSizeFormatter.format(state.dbBytes),
                    )
                    StorageRow(
                        label = stringResource(R.string.privacy_storage_cache),
                        value = ByteSizeFormatter.format(state.cacheBytes),
                    )
                    StorageRow(
                        label = stringResource(R.string.privacy_storage_total),
                        value = ByteSizeFormatter.format(state.totalBytes),
                        emphasize = true,
                    )
                }
                Body(stringResource(R.string.privacy_storage_note))
            }

            SectionCard(
                icon = Icons.Outlined.DeleteSweep,
                title = stringResource(R.string.privacy_actions_title),
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.exportDiagnostics { uri ->
                            runCatching { context.startActivity(viewModel.shareIntentFor(uri)) }
                        }
                    },
                    enabled = !state.exporting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.privacy_action_export), modifier = Modifier.padding(start = 8.dp))
                }
                Body(stringResource(R.string.privacy_action_export_note))

                OutlinedButton(
                    onClick = { showClearDialog = true },
                    enabled = !state.clearingCache,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.privacy_action_clear_cache), modifier = Modifier.padding(start = 8.dp))
                }
                Body(stringResource(R.string.privacy_action_clear_cache_note))

                OutlinedButton(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.privacy_action_logout), modifier = Modifier.padding(start = 8.dp))
                }
                Body(stringResource(R.string.privacy_action_logout_note))
            }
        }
    }

    if (showClearDialog) {
        ConfirmDialog(
            title = stringResource(R.string.privacy_clear_dialog_title),
            message = stringResource(R.string.privacy_clear_dialog_message),
            confirmLabel = stringResource(R.string.privacy_clear_dialog_confirm),
            onConfirm = {
                showClearDialog = false
                viewModel.clearCache()
            },
            onDismiss = { showClearDialog = false },
        )
    }

    if (showLogoutDialog) {
        ConfirmDialog(
            title = stringResource(R.string.privacy_logout_dialog_title),
            message = stringResource(R.string.privacy_logout_dialog_message),
            confirmLabel = stringResource(R.string.privacy_logout_dialog_confirm),
            onConfirm = {
                showLogoutDialog = false
                onLogout()
            },
            onDismiss = { showLogoutDialog = false },
        )
    }
}

@Composable
private fun SectionCard(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            content()
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StorageRow(label: String, value: String, emphasize: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Dns,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = if (emphasize) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        Text(
            text = value,
            style = if (emphasize) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.privacy_dialog_cancel)) } },
    )
}
