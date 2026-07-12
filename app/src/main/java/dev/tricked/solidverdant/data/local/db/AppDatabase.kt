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

private const val DATABASE_VERSION_1 = 1
private const val DATABASE_VERSION_2 = 2
private const val DATABASE_VERSION_3 = 3
private const val DATABASE_VERSION_4 = 4
private const val DATABASE_VERSION_5 = 5
private const val DATABASE_VERSION_6 = 6
private const val DATABASE_VERSION_7 = 7

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
    version = DATABASE_VERSION_7,
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
        val MIGRATION_1_2 = object : Migration(DATABASE_VERSION_1, DATABASE_VERSION_2) {
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
        val MIGRATION_2_3 = object : Migration(DATABASE_VERSION_2, DATABASE_VERSION_3) {
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
        val MIGRATION_3_4 = object : Migration(DATABASE_VERSION_3, DATABASE_VERSION_4) {
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

        /**
         * v4 -> v5 adds sync-conflict-detection columns (SV-027):
         *  - `outbox.baseSnapshotJson`: last server-acked content a local edit was based on.
         *  - `time_entries.conflictServerJson`: full server TimeEntry JSON ("theirs") captured at
         *    conflict-detection time.
         * Both are additive nullable columns; no existing data changes. [SyncState.CONFLICT] is
         * stored as its name via [Converters], same as the existing enum values, so no column-type
         * change is needed for `syncState` itself.
         */
        val MIGRATION_4_5 = object : Migration(DATABASE_VERSION_4, DATABASE_VERSION_5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `outbox` ADD COLUMN `baseSnapshotJson` TEXT")
                db.execSQL("ALTER TABLE `time_entries` ADD COLUMN `conflictServerJson` TEXT")
            }
        }

        /**
         * v5 -> v6 adds account-ownership columns to `entry_templates` (Task 3.1):
         *  - `ownerEndpoint` / `ownerUserId`: the account (API endpoint + user) that owns a
         *    template. Both are nullable; legacy rows keep NULL owners until a later task claims
         *    them. A composite index on (`ownerUserId`, `organizationId`) backs owner-scoped
         *    queries. All changes are additive; no existing data is rewritten.
         */
        val MIGRATION_5_6 = object : Migration(DATABASE_VERSION_5, DATABASE_VERSION_6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `entry_templates` ADD COLUMN `ownerEndpoint` TEXT")
                db.execSQL("ALTER TABLE `entry_templates` ADD COLUMN `ownerUserId` TEXT")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_entry_templates_ownerUserId_organizationId` " +
                        "ON `entry_templates` (`ownerUserId`, `organizationId`)",
                )
            }
        }

        /**
         * v6 -> v7 splits per-source freshness on `sync_meta` (roadmap #35, #79):
         *  - `lastPushAtMs`: last successful outbox flush (push), distinct from the existing pull
         *    `lastFullSyncAtMs`. Nullable; legacy rows keep NULL until the first successful push.
         * Additive nullable column only; no existing data is rewritten.
         */
        val MIGRATION_6_7 = object : Migration(DATABASE_VERSION_6, DATABASE_VERSION_7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sync_meta` ADD COLUMN `lastPushAtMs` INTEGER")
            }
        }

        val MIGRATIONS: Array<Migration> =
            arrayOf(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
            )
    }
}
