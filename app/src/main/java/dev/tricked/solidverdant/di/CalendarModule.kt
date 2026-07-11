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
import dev.tricked.solidverdant.data.repository.TimeEntryReader
import dev.tricked.solidverdant.data.repository.TimeEntryRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarModule {
    @Binds
    @Singleton
    abstract fun bindTimeEntryReader(impl: TimeEntryRepository): TimeEntryReader
}
