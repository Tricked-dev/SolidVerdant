package dev.tricked.solidverdant

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom instrumentation runner that swaps in [HiltTestApplication] as the application under test.
 *
 * Wired via `testInstrumentationRunner` in app/build.gradle.kts. Hilt tests require this so the
 * generated test component is installed instead of the production [SolidVerdantApp] graph. The
 * non-Hilt Compose tests (MonthCalendarViewTest, MainNavHostTest) don't inject anything, so running
 * them under HiltTestApplication is a no-op for them.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
