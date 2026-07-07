/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.review

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.util.NotificationPermissionHelper

/**
 * Material3 time-picker dialog shared by the reminder settings screen and the "adjust end time"
 * review action. Reports the chosen [Int] hour/minute on confirm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReviewTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val is24Hour = remember { android.text.format.DateFormat.is24HourFormat(context) }
    val timeState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = is24Hour,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = timeState)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(timeState.hour, timeState.minute) }) {
                Text(stringResource(R.string.review_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.review_dialog_cancel))
            }
        },
    )
}

/**
 * Dialog listing selectable projects for the "assign project" review action. Uses a lazy list so a
 * large project catalogue is never eagerly composed (per the UI guidance in AGENTS.md).
 */
@Composable
internal fun ProjectPickerDialog(projects: List<ReviewProject>, onSelect: (projectId: String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.review_project_dialog_title)) },
        text = {
            if (projects.isEmpty()) {
                Text(stringResource(R.string.review_project_dialog_empty))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(projects, key = { it.id }) { project ->
                        TextButton(
                            onClick = { onSelect(project.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(projectColor(project.color)),
                                )
                                Text(
                                    text = project.name,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.review_dialog_cancel))
            }
        },
    )
}

@Composable
private fun projectColor(hex: String): Color {
    val fallback = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(hex) {
        runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
    }
}

/** Holder returned by [rememberNotificationPermissionState]. */
internal data class NotificationPermissionState(val hasPermission: Boolean, val request: () -> Unit)

/**
 * Tracks POST_NOTIFICATIONS permission and exposes a request launcher. The status is re-checked on
 * every ON_RESUME so it reflects a grant the user made from system settings after leaving the app.
 * On Android 12 and below notifications need no runtime permission, so this always reports granted.
 */
@Composable
internal fun rememberNotificationPermissionState(): NotificationPermissionState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(NotificationPermissionHelper.hasNotificationPermission(context))
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        granted = isGranted
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = NotificationPermissionHelper.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return NotificationPermissionState(
        hasPermission = granted,
        request = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
    )
}
