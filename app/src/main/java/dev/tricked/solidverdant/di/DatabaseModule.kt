/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.tricked.solidverdant.data.local.db.AppDatabase
import dev.tricked.solidverdant.data.local.db.CatalogDao
import dev.tricked.solidverdant.data.local.db.InboxDismissalDao
import dev.tricked.solidverdant.data.local.db.OutboxDao
import dev.tricked.solidverdant.data.local.db.SyncMetaDao
import dev.tricked.solidverdant.data.local.db.TemplateDao
import dev.tricked.solidverdant.data.local.db.TimeEntryDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "solidverdant.db")
            // No destructive fallback: unsynced outbox operations and pending local edits must
            // survive schema upgrades. Every version bump must ship an explicit migration.
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()

    @Provides fun provideTimeEntryDao(db: AppDatabase): TimeEntryDao = db.timeEntryDao()

    @Provides fun provideCatalogDao(db: AppDatabase): CatalogDao = db.catalogDao()

    @Provides fun provideOutboxDao(db: AppDatabase): OutboxDao = db.outboxDao()

    @Provides fun provideSyncMetaDao(db: AppDatabase): SyncMetaDao = db.syncMetaDao()

    @Provides fun provideTemplateDao(db: AppDatabase): TemplateDao = db.templateDao()

    @Provides fun provideInboxDismissalDao(db: AppDatabase): InboxDismissalDao = db.inboxDismissalDao()
}
