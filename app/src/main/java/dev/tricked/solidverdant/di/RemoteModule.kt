package dev.tricked.solidverdant.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tricked.solidverdant.data.remote.AuthRemoteDataSource
import dev.tricked.solidverdant.data.remote.RemoteDataSource
import dev.tricked.solidverdant.sync.SyncScheduler
import dev.tricked.solidverdant.sync.SyncTrigger
import dev.tricked.solidverdant.util.Clock
import dev.tricked.solidverdant.util.SystemClock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteModule {
    @Binds @Singleton
    abstract fun bindRemoteDataSource(impl: AuthRemoteDataSource): RemoteDataSource

    @Binds @Singleton
    abstract fun bindClock(impl: SystemClock): Clock

    @Binds @Singleton
    abstract fun bindSyncTrigger(impl: SyncScheduler): SyncTrigger
}
