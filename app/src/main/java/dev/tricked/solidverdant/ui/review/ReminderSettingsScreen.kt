/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.review

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.tricked.solidverdant.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Configures the tracking reminder and the end-of-day review (gap analysis #4, #18, #78).
 *
 * Edits the foundation DataStore keys via [ReminderSettingsViewModel] (enabled toggles + reminder
 * time) and re-evaluates the WorkManager schedule after each change. Surfaces the loading state,
 * a denied-notification-permission recovery path, and explains why the time control is unavailable
 * when no reminder is enabled. The reminder time persists across logout like other preferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsScreen(onBack: () -> Unit = {}) {
    val viewModel: ReminderSettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val permission = rememberNotificationPermissionState()
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }

    val timeLabel = remember(state.minuteOfDay) {
        LocalTime.of(state.minuteOfDay / MINUTES_PER_HOUR, state.minuteOfDay % MINUTES_PER_HOUR)
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reminder_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.review_navigate_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.anyEnabled && !permission.hasPermission) {
                PermissionWarningCard(
                    onAllow = permission.request,
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        runCatching { context.startActivity(intent) }
                    },
                )
            }

            Text(
                text = stringResource(R.string.reminder_section_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )

            ToggleRow(
                title = stringResource(R.string.reminder_daily_title),
                subtitle = stringResource(R.string.reminder_daily_subtitle),
                checked = state.reminderEnabled,
                onCheckedChange = viewModel::setReminderEnabled,
            )

            ToggleRow(
                title = stringResource(R.string.reminder_eod_title),
                subtitle = stringResource(R.string.reminder_eod_subtitle),
                checked = state.endOfDayReviewEnabled,
                onCheckedChange = viewModel::setEndOfDayReviewEnabled,
            )

            TimeRow(
                timeLabel = timeLabel,
                enabled = state.anyEnabled,
                onClick = { showTimePicker = true },
            )

            Text(
                text = stringResource(R.string.reminder_best_effort_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    if (showTimePicker) {
        ReviewTimePickerDialog(
            title = stringResource(R.string.reminder_time_dialog_title),
            initialHour = state.minuteOfDay / 60,
            initialMinute = state.minuteOfDay % 60,
            onConfirm = { hour, minute ->
                showTimePicker = false
                viewModel.setReminderTime(hour, minute)
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

private const val MINUTES_PER_HOUR = 60

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The row itself is the click target and carries the label; hide the switch from the
        // accessibility tree so TalkBack announces one toggle, not two overlapping controls.
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun TimeRow(timeLabel: String, enabled: Boolean, onClick: () -> Unit) {
    val rowModifier = if (enabled) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(rowModifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.reminder_time_title),
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (enabled) timeLabel else stringResource(R.string.reminder_time_disabled_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (enabled) {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PermissionWarningCard(onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NotificationsOff, contentDescription = null)
                Text(
                    text = stringResource(R.string.reminder_permission_warning_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = stringResource(R.string.reminder_permission_warning_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onAllow, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.reminder_permission_allow))
                }
                TextButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.reminder_permission_open_settings))
                }
            }
        }
    }
}
