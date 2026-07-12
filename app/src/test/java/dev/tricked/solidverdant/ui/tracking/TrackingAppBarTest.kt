/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.tracking

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import dev.tricked.solidverdant.data.model.Membership
import dev.tricked.solidverdant.data.model.Organization
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SV-006 regression coverage for the Track top-app-bar title ([TrackingAppBarTitle]).
 *
 * The title previously rendered the user name and organization name on adjacent unlabeled lines,
 * colouring the org line `primary` even when it was inert (single membership), giving no dropdown
 * affordance and no accessibility semantics. These tests pin the three fixed behaviours:
 *  - identical user/org names collapse to a single line (no duplicate-text glitch),
 *  - a switchable org line exposes a labelled [Role.Button] plus dropdown-arrow affordance,
 *  - an unswitchable org line is plain, inert text with none of that affordance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackingAppBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun membership(id: String, orgName: String) = Membership(
        id = id,
        role = "member",
        organization = Organization(id = "org-$id", name = orgName, currency = "EUR"),
    )

    private val switchDescription = "Switch organization"

    private val roleButtonMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun identical_user_and_org_names_render_a_single_line() {
        val memberships = listOf(membership("m1", "Tricked"))
        composeRule.setContent {
            MaterialTheme {
                TrackingAppBarTitle(
                    userName = "Tricked",
                    organizationName = "Tricked",
                    canSwitchOrganization = false,
                    memberships = memberships,
                    currentMembershipId = "m1",
                    onMembershipChange = {},
                )
            }
        }

        // Collapsed: "Tricked" appears exactly once, not duplicated across a user + org line.
        composeRule.onAllNodesWithText("Tricked").assertCountEquals(1)
    }

    @Test
    fun switchable_org_line_exposes_a_labelled_role_button_affordance() {
        val memberships = listOf(
            membership("m1", "Acme"),
            membership("m2", "Globex"),
        )
        composeRule.setContent {
            MaterialTheme {
                TrackingAppBarTitle(
                    userName = "Alice",
                    organizationName = "Acme",
                    canSwitchOrganization = true,
                    memberships = memberships,
                    currentMembershipId = "m1",
                    onMembershipChange = {},
                )
            }
        }

        // A single node carries both the "Switch organization" label and Role.Button (the merged
        // Row semantics), and the org name is still visible.
        composeRule.onNodeWithContentDescription(switchDescription)
            .assert(hasContentDescription(switchDescription).and(roleButtonMatcher))
        composeRule.onNodeWithText("Acme").assertExists()
    }

    @Test
    fun single_membership_org_line_is_inert_plain_text() {
        val memberships = listOf(membership("m1", "Acme"))
        composeRule.setContent {
            MaterialTheme {
                TrackingAppBarTitle(
                    userName = "Alice",
                    organizationName = "Acme",
                    canSwitchOrganization = false,
                    memberships = memberships,
                    currentMembershipId = "m1",
                    onMembershipChange = {},
                )
            }
        }

        // No switch affordance: no "Switch organization" node and no Role.Button anywhere...
        composeRule.onNodeWithContentDescription(switchDescription).assertDoesNotExist()
        composeRule.onNode(roleButtonMatcher).assertDoesNotExist()
        // ...but the org name is still shown as plain text.
        composeRule.onNodeWithText("Acme").assertExists()
    }
}
