package dev.tricked.solidverdant

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application class for SolidVerdant
 */
@HiltAndroidApp
class SolidVerdantApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for debug builds
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
