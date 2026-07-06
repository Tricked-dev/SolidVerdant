package dev.tricked.solidverdant.data.local.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun syncStateToString(value: SyncState): String = value.name
    @TypeConverter fun stringToSyncState(value: String): SyncState = SyncState.valueOf(value)

    @TypeConverter fun opTypeToString(value: OutboxOpType): String = value.name
    @TypeConverter fun stringToOpType(value: String): OutboxOpType = OutboxOpType.valueOf(value)
}
