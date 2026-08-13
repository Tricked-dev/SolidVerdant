/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.ui.localization.appLocale
import dev.tricked.solidverdant.ui.theme.Dimens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun EntryDateFieldButton(label: String, date: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier, testTag: String? = null) {
    val locale = appLocale()
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = Dimens.ControlHeightLarge)
            .then(if (testTag == null) Modifier else Modifier.testTag(testTag)),
        contentPadding = PaddingValues(horizontal = Dimens.Space12),
    ) {
        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(Dimens.IconMedium))
        Spacer(Modifier.width(Dimens.Space8))
        Column(horizontalAlignment = Alignment.Start) {
            Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            Text(
                date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)),
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDatePickerDialog(initialDate: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
                modifier = Modifier.testTag(EditTimeEntryTestTags.DATE_PICKER_CONFIRM),
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    ) {
        DatePicker(state = state, modifier = Modifier.testTag(EditTimeEntryTestTags.DATE_PICKER))
    }
}
