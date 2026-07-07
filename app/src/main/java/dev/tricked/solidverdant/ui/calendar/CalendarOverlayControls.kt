/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.ui.components.SectionCard
import dev.tricked.solidverdant.ui.theme.Dimens

/**
 * Opt-in device-calendar overlay controls: the on/off switch, the runtime-permission education and
 * recovery states, and the per-calendar selection. All privacy-sensitive language lives here so the
 * user understands access is local and read-only (FEATURE_GAP_ANALYSIS.md #22/#77).
 */
@Composable
fun CalendarOverlayControls(
    state: CalendarUiState,
    showRationale: Boolean,
    onToggleOverlay: (Boolean) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onToggleCalendar: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        modifier = modifier.fillMaxWidth(),
        title = stringResource(R.string.calendar_overlay_title),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.MinTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.calendar_overlay_show),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.overlayEnabled,
                onCheckedChange = onToggleOverlay,
            )
        }

        if (state.overlayEnabled) {
            when {
                !state.hasCalendarPermission ->
                    PermissionSection(
                        permanentlyDenied = state.permissionRequested && !showRationale,
                        onRequestPermission = onRequestPermission,
                        onOpenAppSettings = onOpenAppSettings,
                    )

                state.availableCalendars.isEmpty() ->
                    StatusText(stringResource(R.string.calendar_overlay_no_calendars))

                else ->
                    CalendarPickerSection(state = state, onToggleCalendar = onToggleCalendar, onRetry = onRetry)
            }
        }
    }
}

@Composable
private fun PermissionSection(permanentlyDenied: Boolean, onRequestPermission: () -> Unit, onOpenAppSettings: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = stringResource(
                if (permanentlyDenied) {
                    R.string.calendar_overlay_denied
                } else {
                    R.string.calendar_overlay_permission_rationale
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrivacyNote()
        if (permanentlyDenied) {
            TextButton(onClick = onOpenAppSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.calendar_overlay_open_settings))
            }
        } else {
            Button(onClick = onRequestPermission, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.calendar_overlay_grant))
            }
        }
    }
}

@Composable
private fun CalendarPickerSection(state: CalendarUiState, onToggleCalendar: (String) -> Unit, onRetry: () -> Unit) {
    var expanded by remember { mutableStateOf(state.selectedCalendarIds.isEmpty()) }
    Column(modifier = Modifier.fillMaxWidth()) {
        PrivacyNote()
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(
                stringResource(
                    R.string.calendar_overlay_choose_count,
                    state.selectedCalendarIds.size,
                ),
            )
        }
        if (expanded) {
            Text(
                text = stringResource(R.string.calendar_overlay_select_prompt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                state.availableCalendars.forEach { calendar ->
                    val checked = calendar.id in state.selectedCalendarIds
                    val desc = stringResource(
                        R.string.calendar_overlay_calendar_desc,
                        calendar.displayName,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { onToggleCalendar(calendar.id) },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(
                                    calendar.colorArgb?.let { Color(it) }
                                        ?: MaterialTheme.colorScheme.secondary,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = calendar.displayName.ifBlank { calendar.accountName },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                            if (calendar.accountName.isNotBlank()) {
                                Text(
                                    text = calendar.accountName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Status for the currently-selected calendars.
        when {
            state.selectedCalendarIds.isEmpty() ->
                StatusText(stringResource(R.string.calendar_overlay_none_selected))

            state.overlayLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                StatusText(stringResource(R.string.calendar_overlay_loading))
            }

            state.overlayError -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                StatusText(stringResource(R.string.calendar_overlay_error))
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.calendar_overlay_retry))
                }
            }

            state.overlayEvents.isEmpty() ->
                StatusText(stringResource(R.string.calendar_overlay_empty))

            else -> StatusText(stringResource(R.string.calendar_overlay_showing))
        }
    }
}

@Composable
private fun PrivacyNote() {
    Text(
        text = stringResource(R.string.calendar_overlay_privacy),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}
