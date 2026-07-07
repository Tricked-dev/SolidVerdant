package dev.tricked.solidverdant.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TimeEntryEntity::class,
        ProjectEntity::class,
        TaskEntity::class,
        TagEntity::class,
        OrganizationEntity::class,
        MembershipEntity::class,
        TimeEntryTagCrossRef::class,
        SyncMetaEntity::class,
        OutboxEntity::class,
        ClientEntity::class,
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timeEntryDao(): TimeEntryDao
    abstract fun catalogDao(): CatalogDao
    abstract fun outboxDao(): OutboxDao
    abstract fun syncMetaDao(): SyncMetaDao
}
