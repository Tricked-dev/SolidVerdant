/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.ui.theme.Dimens

/**
 * The app's bottom-sheet layout, as in the search options: the page background behind grouped
 * sections, a bold title with an optional action beside it, the [content] sections, then a
 * right-aligned Done when [onDone] is given. It always opens fully expanded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    titleAction: (@Composable RowScope.() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    doneLabel: String = stringResource(R.string.done),
    doneTestTag: String? = null,
    dismissible: () -> Boolean = { true },
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { if (dismissible()) onDismiss() },
        modifier = modifier,
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
            SheetTitleRow(title = title, action = titleAction)
            content()
            if (onDone != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.Space16),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onDone, modifier = doneTestTag?.let(Modifier::testTag) ?: Modifier) {
                        Text(doneLabel)
                    }
                }
            }
        }
    }
}

/** A sheet's title, aligned with the text inside its grouped sections, with an optional action. */
@Composable
fun SheetTitleRow(title: String, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.Space16 + Dimens.Space16, end = Dimens.Space16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        action?.invoke(this)
    }
}

/**
 * The app's confirmation dialog: Cancel as a text button, the action as a filled button, red for
 * [destructive] actions such as delete, discard, clear cache or log out.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    confirmTestTag: String? = null,
    dismissTestTag: String? = null,
    dismissLabel: String = stringResource(R.string.cancel),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = message?.let { { Text(it) } },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = confirmTestTag?.let(Modifier::testTag) ?: Modifier,
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = dismissTestTag?.let(Modifier::testTag) ?: Modifier) {
                Text(dismissLabel)
            }
        },
    )
}

/**
 * The app's time picker: the device's 12- or 24-hour clock, Cancel as a text button and the
 * confirm as a filled button, like the date picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    confirmLabel: String = stringResource(R.string.done),
    pickerModifier: Modifier = Modifier,
    confirmTestTag: String = EditTimeEntryTestTags.TIME_PICKER_CONFIRM,
) {
    val context = LocalContext.current
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state, modifier = pickerModifier)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(state.hour, state.minute) },
                modifier = Modifier.testTag(confirmTestTag),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
