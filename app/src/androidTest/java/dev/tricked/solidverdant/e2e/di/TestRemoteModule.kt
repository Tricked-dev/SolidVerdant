/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.e2e.di

import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dev.tricked.solidverdant.data.remote.AuthRemoteDataSource
import dev.tricked.solidverdant.data.remote.RemoteDataSource
import dev.tricked.solidverdant.di.RemoteModule
import dev.tricked.solidverdant.sync.SyncScheduler
import dev.tricked.solidverdant.sync.SyncTrigger
import dev.tricked.solidverdant.util.Clock
import javax.inject.Singleton

/**
 * Test replacement for [RemoteModule].
 *
 * Keeps the production [RemoteDataSource] and [SyncTrigger] wiring (they already talk to whatever
 * endpoint AuthDataStore is seeded with — i.e. MockWebServer in tests), but swaps the [Clock] for a
 * deterministic [TestClock] so synced timestamps are reproducible.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RemoteModule::class])
abstract class TestRemoteModule {
    @Binds
    @Singleton
    abstract fun bindRemoteDataSource(impl: AuthRemoteDataSource): RemoteDataSource

    @Binds
    @Singleton
    abstract fun bindClock(impl: TestClock): Clock

    @Binds
    @Singleton
    abstract fun bindSyncTrigger(impl: SyncScheduler): SyncTrigger
}
