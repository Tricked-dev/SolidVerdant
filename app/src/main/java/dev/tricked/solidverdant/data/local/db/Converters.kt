/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.data.local.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun syncStateToString(value: SyncState): String = value.name

    @TypeConverter fun stringToSyncState(value: String): SyncState = SyncState.valueOf(value)

    @TypeConverter fun opTypeToString(value: OutboxOpType): String = value.name

    @TypeConverter fun stringToOpType(value: String): OutboxOpType = OutboxOpType.valueOf(value)
}
