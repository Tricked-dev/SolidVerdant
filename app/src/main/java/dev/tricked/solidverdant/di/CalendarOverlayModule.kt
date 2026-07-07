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
import dev.tricked.solidverdant.data.calendar.CalendarEventProvider
import dev.tricked.solidverdant.data.calendar.CalendarEventSource
import dev.tricked.solidverdant.data.calendar.CalendarOverlaySettings
import dev.tricked.solidverdant.data.calendar.SettingsCalendarOverlaySettings
import javax.inject.Singleton

/**
 * Binds the device-calendar overlay seams. New module (does not touch the shared DI modules) so the
 * week-calendar/overlay feature can evolve without merge conflicts.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarOverlayModule {
    @Binds
    @Singleton
    abstract fun bindCalendarEventSource(impl: CalendarEventProvider): CalendarEventSource

    @Binds
    @Singleton
    abstract fun bindCalendarOverlaySettings(
        impl: SettingsCalendarOverlaySettings,
    ): CalendarOverlaySettings
}
