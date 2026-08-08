/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.tricked.solidverdant.e2e.robots

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist

/**
 * Base for screen robots. Robots expose high-level, intention-revealing actions/assertions over the
 * [ComposeTestRule] and encapsulate matching (prefer testTags via [dev.tricked.solidverdant.e2e.TestTags]).
 *
 * All waits use Compose's semantics-aware wait helpers — never Thread.sleep — so tests stay deterministic.
 */
abstract class Robot(protected val composeRule: ComposeTestRule) {

    protected fun waitUntilTagExists(tag: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        composeRule.waitUntilAtLeastOneExists(hasTestTag(tag), timeoutMs)
    }

    protected fun waitUntilTagIsGone(tag: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        composeRule.waitUntilDoesNotExist(hasTestTag(tag), timeoutMs)
    }

    protected fun waitUntilTextExists(text: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        composeRule.waitUntilAtLeastOneExists(hasText(text, substring = true), timeoutMs)
    }

    protected fun nodesWithTag(tag: String) = nodes(hasTestTag(tag))

    protected fun firstNodeWithTag(tag: String): SemanticsNodeInteraction = nodes(hasTestTag(tag)).onFirst()

    protected fun waitUntilEnabledTagExists(tag: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        val matcher = hasTestTag(tag) and isEnabled()
        composeRule.waitUntilAtLeastOneExists(matcher, timeoutMs)
    }

    protected fun firstEnabledNodeWithTag(tag: String): SemanticsNodeInteraction = nodes(hasTestTag(tag) and isEnabled()).onFirst()

    /** Test tags often live below a clickable/scrollable production container. */
    private fun nodes(matcher: SemanticsMatcher) = composeRule.onAllNodes(matcher, useUnmergedTree = true)

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}
