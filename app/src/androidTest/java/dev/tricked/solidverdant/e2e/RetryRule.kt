/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e

import android.util.Log
import org.junit.internal.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Re-runs a test up to [attempts] times, passing as soon as one attempt succeeds. Only the final
 * failure propagates. A skipped test ([AssumptionViolatedException]) is never retried.
 *
 * Wrap this OUTSIDE the setup rules (it is the outermost rule in [E2eRule]) so every attempt gets a
 * fresh harness — a new mock server, a clean WorkManager/Room/DataStore — rather than inheriting
 * contaminated state from the failed attempt.
 *
 * It exists to absorb the genuinely flaky flows on CI's slow API-29 emulator (per-second timer
 * ticks, activity recreation) without disabling them. It is NOT a substitute for gating the flows
 * that fail deterministically on API 29 — retrying those only wastes emulator time.
 */
class RetryRule(private val attempts: Int = 3) : TestRule {

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            var lastError: Throwable? = null
            for (attempt in 1..attempts) {
                try {
                    base.evaluate()
                    return
                } catch (skip: AssumptionViolatedException) {
                    throw skip
                } catch (error: Throwable) {
                    lastError = error
                    Log.w(TAG, "${description.displayName} failed attempt $attempt/$attempts", error)
                }
            }
            throw lastError ?: IllegalStateException("RetryRule ran zero attempts")
        }
    }

    private companion object {
        const val TAG = "RetryRule"
    }
}
