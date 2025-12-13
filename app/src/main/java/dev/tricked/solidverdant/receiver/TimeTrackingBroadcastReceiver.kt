/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * This BroadcastReceiver is obsolete and its functionality has been moved to
 * TimeTrackingNotificationService. This file should be deleted.
 */
class TimeTrackingBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // This receiver is no longer in use and should be deleted.
    }
}
