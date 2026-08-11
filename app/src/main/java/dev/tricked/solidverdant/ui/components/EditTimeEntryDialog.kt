/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.data.model.Project
import dev.tricked.solidverdant.data.model.Tag
import dev.tricked.solidverdant.data.model.Task
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.data.model.TimeEntryType
import dev.tricked.solidverdant.domain.time.isRunningTimeEntry
import dev.tricked.solidverdant.ui.theme.Dimens
import dev.tricked.solidverdant.ui.tracking.EntryTimeValidator
import dev.tricked.solidverdant.ui.tracking.EntryTrustRules
import dev.tricked.solidverdant.ui.tracking.EntryValidationBanner
import dev.tricked.solidverdant.ui.tracking.TagsSelector
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import dev.tricked.solidverdant.ui.tracking.ProjectTaskDropdown as TrackingProjectTaskDropdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
fun EditTimeEntryDialog(
    entry: TimeEntry?,
    zone: ZoneId,
    projects: List<Project>,
    tasks: List<Task>,
    tags: List<Tag>,
    onDismiss: () -> Unit,
    onSave: (String?, String?, String?, List<String>, Boolean, String, String?) -> Unit,
    existingEntries: List<TimeEntry> = emptyList(),
    preventOverlap: Boolean = false,
    inlinePresentation: Boolean = false,
    suggestedStart: ZonedDateTime? = null,
    suggestedEnd: ZonedDateTime? = null,
    onDelete: (() -> Unit)? = null,
    isBreak: Boolean = false,
) {
    var description by remember(entry?.id) { mutableStateOf(entry?.description ?: "") }
    var projectId by remember(entry?.id) { mutableStateOf(entry?.projectId) }
    var taskId by remember(entry?.id) { mutableStateOf(entry?.taskId) }
    var selectedTags by remember(entry?.id) { mutableStateOf(entry?.tags?.map { it.id }.orEmpty()) }
    var billable by remember(entry?.id) { mutableStateOf(entry?.billable ?: false) }
    val isRunningEntry = remember(entry?.id, entry?.end, entry?.duration) {
        entry?.let(::isRunningTimeEntry) == true
    }
    val isBreakEntry = isBreak || entry?.type == TimeEntryType.BREAK
    val originalStart = remember(entry?.id, suggestedStart, zone) {
        entry?.let { ZonedDateTime.parse(it.start, DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(zone) }
            ?: (suggestedStart ?: ZonedDateTime.now(zone).minusHours(1))
                .withSecond(0).withNano(0)
    }
    val originalEnd = remember(entry?.id, suggestedEnd, suggestedStart, zone) {
        when {
            suggestedEnd != null -> suggestedEnd.withZoneSameInstant(zone)
            entry?.end != null -> ZonedDateTime.parse(entry.end, DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(zone)
            entry != null -> originalStart.plusSeconds((entry.duration ?: 0).toLong())
            else -> ZonedDateTime.now(originalStart.zone).withSecond(0).withNano(0)
                .let { if (it.isAfter(originalStart)) it else originalStart.plusMinutes(1) }
        }
    }
    var startTime by remember(entry?.id, suggestedStart, zone) { mutableStateOf(originalStart) }
    var endTime by remember(entry?.id, suggestedEnd, suggestedStart, zone) { mutableStateOf(originalEnd) }
    var durationMinutes by remember(entry?.id, suggestedStart, suggestedEnd, zone) {
        mutableStateOf(java.time.Duration.between(originalStart, originalEnd).toMinutes().coerceAtLeast(1).toString())
    }
    var editingTime by remember { mutableStateOf<TimeField?>(null) }
    var editingDate by remember { mutableStateOf<TimeField?>(null) }
    val durationIsValid = isRunningEntry || durationMinutes.toLongOrNull()?.let { it > 0 } == true
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val overlaps = remember(startTime, endTime, existingEntries, isRunningEntry) {
        if (isBreakEntry || isRunningEntry || existingEntries.isEmpty()) {
            false
        } else {
            val candidate = (
                entry ?: TimeEntry(
                    id = "",
                    userId = "",
                    start = startTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    end = endTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    organizationId = existingEntries.firstOrNull()?.organizationId.orEmpty(),
                )
                ).copy(
                start = startTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                end = endTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            )
            existingEntries.any { it.id != candidate.id && EntryTrustRules.overlaps(candidate, it) }
        }
    }
    val validation = remember(startTime, endTime, overlaps, preventOverlap, isRunningEntry) {
        if (isRunningEntry) {
            EntryTimeValidator.Result(error = null, warnings = emptyList())
        } else {
            EntryTimeValidator.evaluate(startTime, endTime, overlaps, preventOverlap)
        }
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(
                    when {
                        isBreakEntry -> R.string.break_entry_title
                        entry == null -> R.string.add_time_entry
                        else -> R.string.edit_time_entry
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = stringResource(R.string.time_and_duration),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
            ) {
                EntryDateFieldButton(
                    label = stringResource(R.string.start_date),
                    date = startTime.toLocalDate(),
                    onClick = { editingDate = TimeField.Start },
                    modifier = if (isRunningEntry) Modifier.fillMaxWidth() else Modifier.weight(1f),
                    testTag = EditTimeEntryTestTags.START_DATE,
                )
                if (!isRunningEntry) {
                    EntryDateFieldButton(
                        label = stringResource(R.string.end_date),
                        date = endTime.toLocalDate(),
                        onClick = { editingDate = TimeField.End },
                        modifier = Modifier.weight(1f),
                        testTag = EditTimeEntryTestTags.END_DATE,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space8),
            ) {
                TimeFieldButton(
                    label = stringResource(R.string.start_time),
                    value = startTime,
                    onClick = { editingTime = TimeField.Start },
                    modifier = (if (isRunningEntry) Modifier.fillMaxWidth() else Modifier.weight(1f))
                        .testTag(EditTimeEntryTestTags.START_TIME),
                )
                if (!isRunningEntry) {
                    TimeFieldButton(
                        label = stringResource(R.string.end_time),
                        value = endTime,
                        onClick = { editingTime = TimeField.End },
                        modifier = Modifier.weight(1f).testTag(EditTimeEntryTestTags.END_TIME),
                    )
                }
            }

            if (isRunningEntry) {
                Text(
                    text = stringResource(R.string.running_entry_start_edit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.total_time),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = formatEditableDuration(durationMinutes.toLongOrNull() ?: 0),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    setDuration(
                                        (durationMinutes.toLongOrNull() ?: MINIMUM_DURATION_MINUTES) - DURATION_STEP_MINUTES,
                                    )
                                },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Default.Remove,
                                    contentDescription = stringResource(
                                        R.string.decrease_15_minutes,
                                    ),
                                )
                            }
                            OutlinedTextField(
                                value = durationMinutes,
                                onValueChange = { value ->
                                    if (value.all(Char::isDigit)) {
                                        durationMinutes = value
                                        value.toLongOrNull()
                                            ?.takeIf { it >= MINIMUM_DURATION_MINUTES }
                                            ?.let { endTime = startTime.plusMinutes(it) }
                                    }
                                },
                                label = { Text(stringResource(R.string.minutes)) },
                                suffix = { Text(stringResource(R.string.minutes_short)) },
                                isError = !durationIsValid,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                            )
                            FilledTonalIconButton(
                                onClick = { setDuration((durationMinutes.toLongOrNull() ?: 0) + DURATION_STEP_MINUTES) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.increase_15_minutes),
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.description)) },
                modifier = Modifier.fillMaxWidth().testTag(EditTimeEntryTestTags.DESCRIPTION_FIELD),
                shape = RoundedCornerShape(8.dp),
            )

            if (!isBreakEntry) {
                TrackingProjectTaskDropdown(
                    selectedProjectId = projectId,
                    selectedTaskId = taskId,
                    projects = projects,
                    tasks = tasks,
                    onSelectionChanged = { newProjectId, newTaskId ->
                        projectId = newProjectId
                        taskId = newTaskId
                    },
                    enabled = true,
                )

                if (tags.isNotEmpty()) {
                    TagsSelector(
                        selectedTagIds = selectedTags,
                        availableTags = tags,
                        onTagsChanged = { selectedTags = it },
                        enabled = true,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = billable,
                            role = Role.Checkbox,
                            onValueChange = { billable = it },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = billable,
                        onCheckedChange = null,
                    )
                    Text(
                        text = stringResource(R.string.billable),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (!isRunningEntry) {
                EntryValidationBanner(result = validation, durationHours = durationHours)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (onDelete != null) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onDelete != null) {
                    Button(
                        onClick = onDelete,
                        modifier = Modifier.testTag(EditTimeEntryTestTags.DELETE_BUTTON),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                    Spacer(Modifier.weight(1f))
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(
                            description.ifEmpty { null },
                            projectId.takeUnless { isBreakEntry },
                            taskId.takeUnless { isBreakEntry },
                            selectedTags.takeUnless { isBreakEntry }.orEmpty(),
                            billable && !isBreakEntry,
                            startTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                            endTime.takeUnless { isRunningEntry }?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        )
                    },
                    enabled = durationIsValid && validation.canSave,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag(EditTimeEntryTestTags.SAVE_BUTTON),
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
                modifier = Modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT_FRACTION),
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
                    startTime = startTime.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                    if (!isRunningEntry) {
                        val minutes = durationMinutes.toLongOrNull() ?: 1
                        endTime = startTime.plusMinutes(minutes)
                    }
                } else {
                    val sameDayEnd = endTime.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                    // Do not silently roll an earlier clock-time into a ~24h entry: only a plausible
                    // overnight span becomes cross-midnight, otherwise keep it same-day so the
                    // validation banner surfaces the end-before-start error for the user to fix.
                    endTime = EntryTimeValidator.resolveEnd(startTime, sameDayEnd) ?: sameDayEnd
                    durationMinutes = java.time.Duration.between(startTime, endTime).toMinutes().toString()
                }
                editingTime = null
            },
        )
    }

    editingDate?.let { field ->
        val current = if (field == TimeField.Start) startTime else endTime
        EntryDatePickerDialog(
            initialDate = current.toLocalDate(),
            onDismiss = { editingDate = null },
            onConfirm = { date ->
                if (field == TimeField.Start) {
                    startTime = startTime.with(date)
                    if (!isRunningEntry) {
                        val minutes = durationMinutes.toLongOrNull() ?: 1
                        endTime = startTime.plusMinutes(minutes)
                    }
                } else {
                    endTime = endTime.with(date)
                    durationMinutes = java.time.Duration.between(startTime, endTime).toMinutes().toString()
                }
                editingDate = null
            },
        )
    }
}

private enum class TimeField { Start, End }

@Composable
private fun TimeFieldButton(label: String, value: ZonedDateTime, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
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
private fun EntryTimePickerDialog(title: String, initial: ZonedDateTime, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { Button(onClick = { onConfirm(state.hour, state.minute) }) { Text(stringResource(R.string.done)) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun formatEditableDuration(minutes: Long): String {
    val hours = minutes / MINUTES_PER_HOUR
    val remainingMinutes = minutes % MINUTES_PER_HOUR
    return when {
        hours > 0 && remainingMinutes > 0 -> "${hours}h ${remainingMinutes}m"
        hours > 0 -> "${hours}h"
        else -> "${remainingMinutes}m"
    }
}

private const val DURATION_STEP_MINUTES = 15L
private const val MINIMUM_DURATION_MINUTES = 1L
private const val MINUTES_PER_HOUR = 60L
private const val SHEET_HEIGHT_FRACTION = 0.9f
