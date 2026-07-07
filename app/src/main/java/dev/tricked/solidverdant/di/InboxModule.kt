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
