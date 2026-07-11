/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tracking

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import dev.tricked.solidverdant.data.model.TimeEntry
import dev.tricked.solidverdant.ui.components.EditTimeEntryDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SV-010 regression: the "Billable" control must be a single semantics node exposing
 * [Role.Checkbox] plus a checked/unchecked toggle state AND the "Billable" text label merged
 * together — previously it was a bare, unlabeled `Checkbox` that a screen reader announced with
 * no name. [EditTimeEntryDialog] hosts the row via `Modifier.toggleable(..., role = Role.Checkbox)`
 * and is rendered here with `inlinePresentation = true` (as the Roborazzi README screenshot suite
 * already does) so it renders standalone without a real ModalBottomSheet / window.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BillableAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun entry(billable: Boolean) = TimeEntry(
        id = "e1",
        description = "work",
        userId = "u1",
        start = "2026-07-06T09:00:00Z",
        end = "2026-07-06T10:00:00Z",
        duration = 3600,
        billable = billable,
        organizationId = "org1",
    )

    private fun setContent(billable: Boolean, onSave: (Boolean) -> Unit = {}) {
        composeRule.setContent {
            MaterialTheme {
                EditTimeEntryDialog(
                    entry = entry(billable),
                    projects = emptyList(),
                    tasks = emptyList(),
                    tags = emptyList(),
                    onDismiss = {},
                    onSave = { _, _, _, _, newBillable, _, _ -> onSave(newBillable) },
                    inlinePresentation = true,
                )
            }
        }
    }

    /** [Role.Checkbox] merged with the checked [ToggleableState] and the "Billable" text label, as one node. */
    private fun billableCheckboxMatcher(checked: Boolean): SemanticsMatcher {
        val expectedState = if (checked) ToggleableState.On else ToggleableState.Off
        return SemanticsMatcher("Role.Checkbox + ToggleableState($expectedState) + text 'Billable'") { node ->
            val hasRole = node.config.getOrElseNullable(SemanticsProperties.Role) { null } == Role.Checkbox
            val hasState = node.config.getOrElseNullable(SemanticsProperties.ToggleableState) { null } == expectedState
            val hasLabel = node.config.getOrElseNullable(SemanticsProperties.Text) { null }
                ?.any { it.text == "Billable" } == true
            hasRole && hasState && hasLabel
        }
    }

    @Test
    fun billable_row_exposes_a_single_merged_checkbox_node_labelled_billable_when_checked() {
        setContent(billable = true)

        // useUnmergedTree defaults to false: onNode() only sees the merged semantics tree, so a
        // match here also proves the role/state/label all live on one merged node rather than
        // being scattered across the Checkbox and a separate, unlabeled Text sibling.
        composeRule.onNode(billableCheckboxMatcher(checked = true)).assertExists()
    }

    @Test
    fun billable_row_reflects_unchecked_state_via_the_same_merged_node() {
        setContent(billable = false)

        composeRule.onNode(billableCheckboxMatcher(checked = false)).assertExists()
    }

    @Test
    fun billable_row_is_a_single_operable_toggle_node() {
        setContent(billable = true)

        // The merged Role.Checkbox row (carrying the "Billable" label, not just the small Checkbox
        // glyph) must expose an OnClick action so a screen reader / keyboard can operate the whole
        // labelled row as one control - the operability guarantee behind the SV-010 NAF fix.
        composeRule.onNode(billableCheckboxMatcher(checked = true))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
    }
}
