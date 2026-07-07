package dev.tricked.solidverdant.screenshots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import dev.tricked.solidverdant.data.local.AppThemeMode
import dev.tricked.solidverdant.ui.theme.SolidVerdantTheme
import java.io.File

/**
 * Pure-JVM (Robolectric + Roborazzi) screenshot host.
 *
 * The whole style matrix is expressed as two axis enums plus [ScreenshotMatrix]; that object is the
 * single place to edit. To add a locale (nl/ja) or another device (foldable) later, append a new
 * entry to the relevant axis and, for locale, wrap [render]'s content in a locale override — no test
 * code changes required elsewhere.
 */
enum class ThemeAxis(val id: String, val mode: AppThemeMode) {
    /** The Verdant light scheme. */
    LIGHT("light", AppThemeMode.LIGHT),

    /** The "Neo" dark scheme — the cohesive README hero style. */
    DARK("dark", AppThemeMode.NEO),
}

enum class DeviceAxis(val id: String, val widthDp: Int, val heightDp: Int) {
    PHONE("phone", 411, 891),
    TABLET("tablet", 800, 1280),
}

/** The single list to edit. Cartesian product of these axes is rendered for every screen. */
object ScreenshotMatrix {
    val themes: List<ThemeAxis> = listOf(ThemeAxis.LIGHT, ThemeAxis.DARK)
    val devices: List<DeviceAxis> = listOf(DeviceAxis.PHONE, DeviceAxis.TABLET)

    /** The cohesive README hero style: Neo dark + phone. */
    val readmeTheme: ThemeAxis = ThemeAxis.DARK
    val readmeDevice: DeviceAxis = DeviceAxis.PHONE
}

object ScreenshotHost {

    /** Repository root (the folder that owns settings.gradle.kts), regardless of Gradle's cwd. */
    private val repoRoot: File by lazy {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile
        }
        dir
    }

    /** Absolute path under the repo root, e.g. outputPath("screenshots", "readme", "track.png"). */
    fun outputPath(vararg parts: String): String =
        File(repoRoot, parts.joinToString(File.separator)).absolutePath

    /**
     * Render [content] wrapped in the app theme at the given device size and capture it to
     * [filePath]. Each call spins up (and tears down) its own composition, so it is safe to call
     * many times inside a single test method.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    fun capture(
        theme: ThemeAxis,
        device: DeviceAxis,
        filePath: String,
        content: @Composable () -> Unit,
    ) {
        captureRoboImage(
            filePath = filePath,
            roborazziComposeOptions = RoborazziComposeOptions {
                size(widthDp = device.widthDp, heightDp = device.heightDp)
            },
        ) {
            SolidVerdantTheme(themeMode = theme.mode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    content()
                }
            }
        }
    }
}
