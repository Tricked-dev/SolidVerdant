/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

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
        TemplateEntity::class,
        InboxDismissalEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timeEntryDao(): TimeEntryDao
    abstract fun catalogDao(): CatalogDao
    abstract fun outboxDao(): OutboxDao
    abstract fun syncMetaDao(): SyncMetaDao
    abstract fun templateDao(): TemplateDao
    abstract fun inboxDismissalDao(): InboxDismissalDao

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
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_clients_organizationId` " +
                        "ON `clients` (`organizationId`)",
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

        /**
         * v3 -> v4 adds the Phase 2 review-loop tables. Both are additive CREATE TABLEs plus their
         * `organizationId` indices, matching the Room-generated schema for [TemplateEntity] and
         * [InboxDismissalEntity]. No existing table changes, so no data migration is required.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `entry_templates` (" +
                        "`id` TEXT NOT NULL, `organizationId` TEXT NOT NULL, `name` TEXT, " +
                        "`projectId` TEXT, `taskId` TEXT, `description` TEXT, " +
                        "`tagIds` TEXT NOT NULL, `billable` INTEGER NOT NULL, " +
                        "`isFavorite` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                        "`createdAtMs` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_entry_templates_organizationId` " +
                        "ON `entry_templates` (`organizationId`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `inbox_dismissals` (" +
                        "`issueKey` TEXT NOT NULL, `organizationId` TEXT NOT NULL, " +
                        "`dismissedAtMs` INTEGER NOT NULL, PRIMARY KEY(`issueKey`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_inbox_dismissals_organizationId` " +
                        "ON `inbox_dismissals` (`organizationId`)",
                )
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}
