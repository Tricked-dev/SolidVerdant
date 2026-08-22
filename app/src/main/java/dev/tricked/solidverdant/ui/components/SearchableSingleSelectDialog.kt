/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.tricked.solidverdant.R
import dev.tricked.solidverdant.ui.theme.Dimens

/** Adaptive, searchable single-selection surface for potentially large catalogues. */
@Composable
fun SearchableSingleSelectDialog(
    title: String,
    searchPlaceholder: String,
    allLabel: String,
    options: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    searchTestTag: String? = null,
    optionTestTag: ((String) -> String)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = if (query.isBlank()) {
        options
    } else {
        options.filter { (_, name) -> name.contains(query.trim(), ignoreCase = true) }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val compact = maxWidth < Dimens.NarrowCalendarWidth
            Surface(
                modifier = if (compact) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = Dimens.PickerMaxWidth)
                        .heightIn(max = Dimens.PickerMaxHeight)
                },
                shape = if (compact) RectangleShape else MaterialTheme.shapes.extraLarge,
                tonalElevation = if (compact) Dimens.Space1 else Dimens.Space8,
            ) {
                Column(
                    modifier = (if (compact) Modifier.fillMaxSize().safeDrawingPadding() else Modifier.fillMaxWidth())
                        .padding(Dimens.Space16),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(searchTestTag?.let(Modifier::testTag) ?: Modifier),
                        placeholder = { Text(searchPlaceholder) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
                                }
                            }
                        } else {
                            null
                        },
                        singleLine = true,
                    )
                    LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                        item(key = "all") {
                            SelectablePickerItem(
                                text = allLabel,
                                selected = selectedId == null,
                                onClick = {
                                    onSelect(null)
                                    onDismiss()
                                },
                            )
                        }
                        items(filtered, key = { it.first }) { (id, name) ->
                            SelectablePickerItem(
                                text = name,
                                selected = selectedId == id,
                                onClick = {
                                    onSelect(id)
                                    onDismiss()
                                },
                                modifier = optionTestTag?.let { Modifier.testTag(it(id)) } ?: Modifier,
                            )
                        }
                        if (filtered.isEmpty()) {
                            item(key = "empty") {
                                Text(
                                    text = stringResource(R.string.no_results_found),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectablePickerItem(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DropdownMenuItem(
        text = { Text(text, maxLines = 2) },
        onClick = onClick,
        modifier = modifier,
        trailingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null) }
        } else {
            null
        },
    )
}
