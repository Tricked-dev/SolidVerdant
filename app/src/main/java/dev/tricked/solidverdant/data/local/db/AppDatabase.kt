package dev.tricked.solidverdant.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timeEntryDao(): TimeEntryDao
    abstract fun catalogDao(): CatalogDao
    abstract fun outboxDao(): OutboxDao
    abstract fun syncMetaDao(): SyncMetaDao

    companion object {
        /**
         * v1 -> v2 added the offline `clients` cache table (ClientEntity). No other table changed
         * between these versions, so the migration is a single CREATE TABLE + its index, matching
         * the Room-generated schema for [ClientEntity].
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `clients` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`isArchived` INTEGER NOT NULL, `organizationId` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_clients_organizationId` " +
                        "ON `clients` (`organizationId`)"
                )
            }
        }

        /**
         * v2 -> v3 added durable sync-safety columns to the `outbox` table:
         *  - `clientId`: stable idempotency key per operation.
         *  - `deadLettered`: terminal dead-letter flag for permanently failed operations.
         * Existing rows keep an empty key and a non-dead-lettered state.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `outbox` ADD COLUMN `clientId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `outbox` ADD COLUMN `deadLettered` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
