/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tricked.solidverdant.data.repository.InboxRepository
import dev.tricked.solidverdant.data.repository.InboxRepositoryImpl
import javax.inject.Singleton

/**
 * Binds the Time Inbox seam. New module (does not touch shared DI modules) so the Inbox feature
 * agent can swap the implementation without merge conflicts.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class InboxModule {
    @Binds
    @Singleton
    abstract fun bindInboxRepository(impl: InboxRepositoryImpl): InboxRepository
}
