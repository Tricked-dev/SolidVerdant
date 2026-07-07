package dev.tricked.solidverdant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.ui.tracking.EntryTimeValidator
import dev.tricked.solidverdant.ui.tracking.EntryTrustRules
import dev.tricked.solidverdant.ui.tracking.EntryValidationBanner
import dev.tricked.solidverdant.ui.tracking.ProjectTaskDropdown as TrackingProjectTaskDropdown
import dev.tricked.solidverdant.ui.tracking.TagsSelector
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTimeEntryDialog(
    entry: TimeEntry,
    projects: List<Project>,
    tasks: List<Task>,
    tags: List<Tag>,
    onDismiss: () -> Unit,
    onSave: (String?, String?, String?, List<String>, Boolean, String, String) -> Unit,
    existingEntries: List<TimeEntry> = emptyList(),
    preventOverlap: Boolean = false,
    inlinePresentation: Boolean = false,
) {
    var description by remember { mutableStateOf(entry.description ?: "") }
    var projectId by remember { mutableStateOf(entry.projectId) }
    var taskId by remember { mutableStateOf(entry.taskId) }
    var selectedTags by remember { mutableStateOf(entry.tags.map { it.id }) }
    var billable by remember { mutableStateOf(entry.billable) }
    val originalStart = remember(entry.id) { ZonedDateTime.parse(entry.start, DateTimeFormatter.ISO_DATE_TIME) }
    val originalEnd = remember(entry.id) {
        entry.end?.let { ZonedDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME) }
            ?: originalStart.plusSeconds((entry.duration ?: 0).toLong())
    }
    var startTime by remember(entry.id) { mutableStateOf(originalStart) }
    var endTime by remember(entry.id) { mutableStateOf(originalEnd) }
    var durationMinutes by remember(entry.id) {
        mutableStateOf(java.time.Duration.between(originalStart, originalEnd).toMinutes().coerceAtLeast(1).toString())
    }
    var editingTime by remember { mutableStateOf<TimeField?>(null) }
    val durationIsValid = durationMinutes.toLongOrNull()?.let { it > 0 } == true
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val overlaps = remember(startTime, endTime, existingEntries) {
        if (existingEntries.isEmpty()) {
            false
        } else {
            val candidate = entry.copy(
                start = startTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                end = endTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            )
            existingEntries.any { it.id != candidate.id && EntryTrustRules.overlaps(candidate, it) }
        }
    }
    val validation = remember(startTime, endTime, overlaps, preventOverlap) {
        EntryTimeValidator.evaluate(startTime, endTime, overlaps, preventOverlap)
    }
    val durationHours = remember(startTime, endTime) {
        java.time.Duration.between(startTime, endTime).toHours().coerceAtLeast(0)
    }

    fun setDuration(minutes: Long) {
        val safeMinutes = minutes.coerceAtLeast(1)
        durationMinutes = safeMinutes.toString()
        endTime = startTime.plusMinutes(safeMinutes)
    }

    val sheetContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                Text(
                    text = stringResource(R.string.edit_time_entry),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = stringResource(R.string.time_and_duration),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TimeFieldButton(
                        label = stringResource(R.string.start_time),
                        value = startTime,
                        onClick = { editingTime = TimeField.Start },
                        modifier = Modifier.weight(1f)
                    )
                    TimeFieldButton(
                        label = stringResource(R.string.end_time),
                        value = endTime,
                        onClick = { editingTime = TimeField.End },
                        modifier = Modifier.weight(1f)
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.total_time),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatEditableDuration(durationMinutes.toLongOrNull() ?: 0),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = { setDuration((durationMinutes.toLongOrNull() ?: 1) - 15) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Remove,
                                    contentDescription = stringResource(R.string.decrease_15_minutes)
                                )
                            }
                            OutlinedTextField(
                                value = durationMinutes,
                                onValueChange = { value ->
                                    if (value.all(Char::isDigit)) {
                                        durationMinutes = value
                                        value.toLongOrNull()?.takeIf { it > 0 }?.let { endTime = startTime.plusMinutes(it) }
                                    }
                                },
                                label = { Text(stringResource(R.string.minutes)) },
                                suffix = { Text(stringResource(R.string.minutes_short)) },
                                isError = !durationIsValid,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            FilledTonalIconButton(
                                onClick = { setDuration((durationMinutes.toLongOrNull() ?: 0) + 15) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.increase_15_minutes)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                TrackingProjectTaskDropdown(
                    selectedProjectId = projectId,
                    selectedTaskId = taskId,
                    projects = projects,
                    tasks = tasks,
                    onSelectionChanged = { newProjectId, newTaskId ->
                        projectId = newProjectId
                        taskId = newTaskId
                    },
                    enabled = true
                )

                if (tags.isNotEmpty()) {
                    TagsSelector(
                        selectedTagIds = selectedTags,
                        availableTags = tags,
                        onTagsChanged = { selectedTags = it },
                        enabled = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = billable,
                        onCheckedChange = { billable = it }
                    )
                    Text(
                        text = stringResource(R.string.billable),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                EntryValidationBanner(result = validation, durationHours = durationHours)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                description.ifEmpty { null },
                                projectId,
                                taskId,
                                selectedTags,
                                billable,
                                startTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                                endTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            )
                        },
                        enabled = durationIsValid && validation.canSave,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold)
                    }
                }
        }
    }

    if (inlinePresentation) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                sheetContent()
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            sheetContent()
        }
    }

    editingTime?.let { field ->
        val current = if (field == TimeField.Start) startTime else endTime
        EntryTimePickerDialog(
            title = stringResource(if (field == TimeField.Start) R.string.start_time else R.string.end_time),
            initial = current,
            onDismiss = { editingTime = null },
            onConfirm = { hour, minute ->
                if (field == TimeField.Start) {
                    val minutes = durationMinutes.toLongOrNull() ?: 1
                    startTime = startTime.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                    endTime = startTime.plusMinutes(minutes)
                } else {
                    val sameDayEnd = endTime.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                    // Do not silently roll an earlier clock-time into a ~24h entry: only a plausible
                    // overnight span becomes cross-midnight, otherwise keep it same-day so the
                    // validation banner surfaces the end-before-start error for the user to fix.
                    endTime = EntryTimeValidator.resolveEnd(startTime, sameDayEnd) ?: sameDayEnd
                    durationMinutes = java.time.Duration.between(startTime, endTime).toMinutes().toString()
                }
                editingTime = null
            }
        )
    }
}

private enum class TimeField { Start, End }

@Composable
private fun TimeFieldButton(
    label: String,
    value: ZonedDateTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryTimePickerDialog(
    title: String,
    initial: ZonedDateTime,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { Button(onClick = { onConfirm(state.hour, state.minute) }) { Text(stringResource(R.string.done)) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

private fun formatEditableDuration(minutes: Long): String {
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours > 0 && remainingMinutes > 0 -> "${hours}h ${remainingMinutes}m"
        hours > 0 -> "${hours}h"
        else -> "${remainingMinutes}m"
    }
}
