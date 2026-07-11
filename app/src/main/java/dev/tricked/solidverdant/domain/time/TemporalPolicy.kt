/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.domain.time

import java.time.DayOfWeek
import java.time.ZoneId

/**
 * Account-scoped temporal policy for reporting surfaces. Sourced from the Solidtime profile
 * (`timezone` + `week_start`) rather than [ZoneId.systemDefault], so day/week boundaries in reports
 * match the account's configuration. Reminder scheduling stays device-local and does not use this.
 */
data class TemporalPolicy(val zone: ZoneId, val firstDayOfWeek: DayOfWeek)
