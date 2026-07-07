package dev.tricked.solidverdant.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.domain.inbox.InboxCheckConfig
import dev.tricked.solidverdant.domain.inbox.InboxSettingsDataStore.InboxCheck
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Bottom sheet for the local Time Inbox configuration (gap analysis #17): working days/hours, the
 * minimum gap, the long-entry threshold, and which checks are active. All writes go straight to the
 * ViewModel, which persists them; the sheet reads back the effective [InboxCheckConfig] from state.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InboxSettingsSheet(
    state: InboxUiState,
    viewModel: InboxViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val config = state.config
    var editingWindow by remember { mutableStateOf<WorkField?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.inbox_settings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            // Working days
            SectionLabel(stringResource(R.string.inbox_settings_work_days))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayOfWeek.values().forEach { day ->
                    val selected = day in config.workDays
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val updated = if (selected) config.workDays - day else config.workDays + day
                            viewModel.setWorkDays(updated)
                        },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                    )
                }
            }

            // Working hours
            SectionLabel(stringResource(R.string.inbox_settings_working_hours))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { editingWindow = WorkField.START },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.inbox_settings_work_start) + ": " + minuteText(config.workStartMinute))
                }
                OutlinedButton(
                    onClick = { editingWindow = WorkField.END },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.inbox_settings_work_end) + ": " + minuteText(config.workEndMinute))
                }
            }

            // Minimum gap
            Stepper(
                label = stringResource(R.string.inbox_settings_min_gap),
                value = stringResource(R.string.inbox_minutes_value, config.minGapMinutes),
                onDecrease = { viewModel.setMinGapMinutes((config.minGapMinutes - 5).coerceAtLeast(1)) },
                onIncrease = { viewModel.setMinGapMinutes(config.minGapMinutes + 5) },
                decreaseCd = stringResource(R.string.inbox_settings_decrease),
                increaseCd = stringResource(R.string.inbox_settings_increase),
            )

            // Long-entry threshold
            Stepper(
                label = stringResource(R.string.inbox_settings_max_duration),
                value = stringResource(R.string.inbox_hours_value, config.maxDurationHours),
                onDecrease = { viewModel.setMaxDurationHours((config.maxDurationHours - 1).coerceAtLeast(1)) },
                onIncrease = { viewModel.setMaxDurationHours((config.maxDurationHours + 1).coerceAtMost(24)) },
                decreaseCd = stringResource(R.string.inbox_settings_decrease),
                increaseCd = stringResource(R.string.inbox_settings_increase),
            )

            // Checks
            SectionLabel(stringResource(R.string.inbox_settings_checks))
            CheckToggle(stringResource(R.string.inbox_settings_check_gaps), config.checkGaps) {
                viewModel.setCheckEnabled(InboxCheck.GAPS, it)
            }
            CheckToggle(stringResource(R.string.inbox_settings_check_overlaps), config.checkOverlaps) {
                viewModel.setCheckEnabled(InboxCheck.OVERLAPS, it)
            }
            CheckToggle(stringResource(R.string.inbox_settings_check_missing_project), config.checkMissingProject) {
                viewModel.setCheckEnabled(InboxCheck.MISSING_PROJECT, it)
            }
            CheckToggle(stringResource(R.string.inbox_settings_check_missing_task), config.checkMissingTask) {
                viewModel.setCheckEnabled(InboxCheck.MISSING_TASK, it)
            }
            CheckToggle(stringResource(R.string.inbox_settings_check_missing_description), config.checkMissingDescription) {
                viewModel.setCheckEnabled(InboxCheck.MISSING_DESCRIPTION, it)
            }
            CheckToggle(stringResource(R.string.inbox_settings_check_missing_tags), config.checkMissingTags) {
                viewModel.setCheckEnabled(InboxCheck.MISSING_TAGS, it)
            }
            CheckToggle(stringResource(R.string.inbox_settings_check_long), config.checkLongDuration) {
                viewModel.setCheckEnabled(InboxCheck.LONG_DURATION, it)
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.inbox_settings_done))
            }
        }
    }

    editingWindow?.let { field ->
        val initialMinute = if (field == WorkField.START) config.workStartMinute else config.workEndMinute
        WorkTimePickerDialog(
            initialMinute = initialMinute,
            onDismiss = { editingWindow = null },
            onConfirm = { minute ->
                if (field == WorkField.START) {
                    viewModel.setWorkWindow(minute, config.workEndMinute)
                } else {
                    viewModel.setWorkWindow(config.workStartMinute, minute)
                }
                editingWindow = null
            },
        )
    }
}

private enum class WorkField { START, END }

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseCd: String,
    increaseCd: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = onDecrease, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = decreaseCd)
        }
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        FilledTonalIconButton(onClick = onIncrease, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Add, contentDescription = increaseCd)
        }
    }
}

@Composable
private fun CheckToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkTimePickerDialog(
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = (initialMinute / 60).coerceIn(0, 23),
        initialMinute = initialMinute % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.inbox_settings_done))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.inbox_dismiss)) }
        },
        text = { TimePicker(state = state) },
    )
}

private fun minuteText(minuteOfDay: Int): String {
    val clamped = minuteOfDay.coerceIn(0, 1440)
    val h = clamped / 60
    val m = clamped % 60
    return "%02d:%02d".format(h, m)
}
