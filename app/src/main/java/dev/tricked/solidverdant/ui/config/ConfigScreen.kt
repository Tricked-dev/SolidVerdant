/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.config

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.ui.auth.OAuthConfigState
import dev.tricked.solidverdant.ui.components.ConsumeVerticalOverscroll
import dev.tricked.solidverdant.ui.components.EntrySheetHeader
import dev.tricked.solidverdant.ui.components.GroupedDivider
import dev.tricked.solidverdant.ui.components.GroupedRow
import dev.tricked.solidverdant.ui.components.GroupedSection
import dev.tricked.solidverdant.ui.theme.Dimens

/** Stable tags for the server configuration sheet. */
object ConfigTestTags {
    const val ENDPOINT_FIELD = "config_endpoint_field"
    const val CLIENT_ID_FIELD = "config_client_id_field"
    const val CANCEL = "config_cancel"
    const val SAVE = "config_save"
    const val TEST_CONNECTION = "config_test_connection"
    const val PASTE = "config_paste"
    const val RESET = "config_reset"
}

/**
 * The server (OAuth) configuration sheet in the entry-form layout: Cancel, the title and Save across
 * the top, the endpoint and client ID as fields in one grouped section, then the actions as rows.
 *
 * The clipboard is only read when the user taps "Paste from clipboard": pasting on every resume
 * overwrote whatever had been typed meanwhile (and made Android announce a clipboard read each
 * time). Coming back to the sheet only refreshes whether there is anything to paste.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
fun ConfigScreen(
    configState: OAuthConfigState,
    onSave: (String, String) -> Unit,
    onReset: () -> Unit,
    onTestConnection: (String, String) -> Unit,
    onDismiss: () -> Unit,
    // Optional review-loop entry points. Rendered only when provided so the pre-login OAuth sheet
    // stays unchanged while an in-app settings caller can surface reminder/template configuration.
    onOpenReminderSettings: (() -> Unit)? = null,
    onOpenManageTemplates: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var endpoint by rememberSaveable { mutableStateOf(configState.endpoint) }
    var clientId by rememberSaveable { mutableStateOf(configState.clientId) }
    var clipboardHasText by remember { mutableStateOf(clipboardHasText(context)) }
    var nothingToPaste by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) clipboardHasText = clipboardHasText(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val paste: () -> Unit = {
        val parsed = readClipboardText(context)?.let(::parseOAuthConfig)
        nothingToPaste = parsed?.endpoint == null && parsed?.clientId == null
        parsed?.endpoint?.let { endpoint = it }
        parsed?.clientId?.let { clientId = it }
    }
    val fieldsComplete = endpoint.isNotBlank() && clientId.isNotBlank()
    val canTest = fieldsComplete && !configState.isTesting

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Only the drag handle may dismiss the sheet; swipes in the content just scroll it.
                .nestedScroll(ConsumeVerticalOverscroll)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = Dimens.Space24),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space16),
        ) {
            Box(Modifier.padding(horizontal = Dimens.Space8)) {
                EntrySheetHeader(
                    title = stringResource(R.string.oauth_configuration),
                    onCancel = onDismiss,
                    onSave = {
                        onSave(endpoint.removeSuffix("/"), clientId)
                        onDismiss()
                    },
                    saveEnabled = fieldsComplete,
                    cancelTag = ConfigTestTags.CANCEL,
                    saveTag = ConfigTestTags.SAVE,
                )
            }

            GroupedSection(header = stringResource(R.string.server_information)) {
                ConfigField(
                    label = stringResource(R.string.server_endpoint),
                    value = endpoint,
                    onValueChange = { endpoint = it.trim() },
                    placeholder = stringResource(R.string.server_endpoint_placeholder),
                    keyboardType = KeyboardType.Uri,
                    testTag = ConfigTestTags.ENDPOINT_FIELD,
                )
                GroupedDivider()
                ConfigField(
                    label = stringResource(R.string.client_id),
                    value = clientId,
                    onValueChange = { clientId = it.trim() },
                    placeholder = null,
                    keyboardType = KeyboardType.Ascii,
                    testTag = ConfigTestTags.CLIENT_ID_FIELD,
                )
            }

            GroupedSection {
                GroupedRow(
                    title = stringResource(if (configState.isTesting) R.string.testing_connection else R.string.test_connection),
                    subtitle = configState.testMessage,
                    leadingIcon = Icons.Outlined.NetworkCheck,
                    onClick = if (canTest) ({ onTestConnection(endpoint, clientId) }) else null,
                    showChevron = false,
                    trailing = { TestResult(isTesting = configState.isTesting, success = configState.testSuccess) },
                    modifier = Modifier
                        .testTag(ConfigTestTags.TEST_CONNECTION)
                        .alpha(if (canTest || configState.isTesting) 1f else DISABLED_ALPHA),
                )
                GroupedDivider(inset = Dimens.SettingsIconInset)
                GroupedRow(
                    title = stringResource(R.string.paste_from_clipboard),
                    subtitle = when {
                        nothingToPaste -> stringResource(R.string.sweep_config_nothing_to_paste)
                        !clipboardHasText -> stringResource(R.string.sweep_config_clipboard_empty)
                        else -> null
                    },
                    leadingIcon = Icons.Outlined.ContentPaste,
                    onClick = paste,
                    showChevron = false,
                    modifier = Modifier.testTag(ConfigTestTags.PASTE),
                )
            }

            if (onOpenReminderSettings != null || onOpenManageTemplates != null) {
                GroupedSection {
                    onOpenReminderSettings?.let { open ->
                        GroupedRow(
                            title = stringResource(R.string.review_menu_reminder_settings),
                            leadingIcon = Icons.Outlined.NotificationsActive,
                            onClick = open,
                        )
                    }
                    if (onOpenReminderSettings != null && onOpenManageTemplates != null) {
                        GroupedDivider(inset = Dimens.SettingsIconInset)
                    }
                    onOpenManageTemplates?.let { open ->
                        GroupedRow(
                            title = stringResource(R.string.review_menu_manage_templates),
                            leadingIcon = Icons.Outlined.Star,
                            onClick = open,
                        )
                    }
                }
            }

            GroupedSection {
                GroupedRow(
                    title = stringResource(R.string.reset_to_defaults),
                    leadingIcon = Icons.Outlined.RestartAlt,
                    destructive = true,
                    onClick = {
                        onReset()
                        onDismiss()
                    },
                    modifier = Modifier.testTag(ConfigTestTags.RESET),
                )
            }
        }
    }
}

/** A labelled, borderless field inside a grouped section, like the entry form's description. */
@Composable
private fun ConfigField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String?,
    keyboardType: KeyboardType,
    testTag: String,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/** The connection test's state at the end of its row: a spinner, or a tick or error mark with its meaning. */
@Composable
private fun TestResult(isTesting: Boolean, success: Boolean?) {
    when {
        isTesting -> CircularProgressIndicator(modifier = Modifier.size(Dimens.IconSmall), strokeWidth = Dimens.Space2)
        success == true -> Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = stringResource(R.string.sweep_config_test_passed),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimens.IconSmall),
        )
        success == false -> Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = stringResource(R.string.sweep_config_test_failed),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(Dimens.IconSmall),
        )
    }
}

/** Whether the clipboard holds text, from its description only: this does not read (or announce reading) it. */
private fun clipboardHasText(context: Context): Boolean {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val description = clipboard.primaryClipDescription ?: return false
    return description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
        description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) ||
        description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)
}

private fun readClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
}

private const val DISABLED_ALPHA = 0.38f
