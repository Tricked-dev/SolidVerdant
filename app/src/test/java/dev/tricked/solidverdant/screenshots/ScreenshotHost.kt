/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.screenshots

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import dev.tricked.solidverdant.data.local.AppThemeMode
import dev.tricked.solidverdant.ui.navigation.MainNavigationBar
import dev.tricked.solidverdant.ui.navigation.Screen
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

    /** Deterministic Pixel-style system chrome surrounding the app content. */
    @Composable
    private fun AndroidShell(content: @Composable () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "09:41",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(
                        modifier = Modifier.size(16.dp),
                    ) {
                        val strokeWidth = 2.dp.toPx()
                        val dotRadius = 1.25.dp.toPx()
                        val inset = 1.dp.toPx()
                        val centerX = size.width / 2f
                        val bottom = size.height - 2.dp.toPx()
                        drawArc(
                            color = Color.White,
                            startAngle = 225f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                            style = Stroke(strokeWidth, cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = Color.White,
                            startAngle = 225f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(inset + 3.dp.toPx(), inset + 3.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(
                                size.width - (inset + 3.dp.toPx()) * 2,
                                size.height - (inset + 3.dp.toPx()) * 2,
                            ),
                            style = Stroke(strokeWidth, cap = StrokeCap.Round),
                        )
                        drawCircle(Color.White, dotRadius, Offset(centerX, bottom))
                    }
                    Text(
                        text = "87%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
            Box(
                modifier = Modifier.fillMaxWidth().height(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.28f)
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.onBackground),
                )
            }
        }
    }

    /** Hosts feature content inside the same app scaffold and bottom navigation used in production. */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AppShell(destination: Screen, inboxBadgeCount: Int = 0, content: @Composable () -> Unit) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0),
            topBar = {
                if (destination == Screen.Track) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = stringResource(dev.tricked.solidverdant.R.string.time_tracking),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Alex Morgan",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "Acme Studio",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = {}) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = stringResource(
                                        dev.tricked.solidverdant.R.string.settings_menu,
                                    ),
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = {}) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(
                                        dev.tricked.solidverdant.R.string.add_time_entry,
                                    ),
                                )
                            }
                            IconButton(onClick = {}) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(
                                        dev.tricked.solidverdant.R.string.refresh,
                                    ),
                                )
                            }
                        },
                    )
                } else {
                    TopAppBar(
                        title = { Text(stringResource(destination.labelRes)) },
                    )
                }
            },
            bottomBar = {
                MainNavigationBar(
                    currentRoute = destination.route,
                    inboxBadgeCount = inboxBadgeCount,
                    onNavigate = {},
                )
            },
        ) { innerPadding ->
            Surface(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                color = MaterialTheme.colorScheme.background,
            ) {
                content()
            }
        }
    }

    /** Repository root (the folder that owns settings.gradle.kts), regardless of Gradle's cwd. */
    private val repoRoot: File by lazy {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile
        }
        dir
    }

    /** Absolute path under the repo root, e.g. outputPath(".github", "screenshots", "readme", "track.png"). */
    fun outputPath(vararg parts: String): String = File(repoRoot, parts.joinToString(File.separator)).absolutePath

    /**
     * Render [content] wrapped in the app theme at the given device size and capture it to
     * [filePath]. Each call spins up (and tears down) its own composition, so it is safe to call
     * many times inside a single test method.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    fun capture(theme: ThemeAxis, device: DeviceAxis, filePath: String, content: @Composable () -> Unit) {
        captureRoboImage(
            filePath = filePath,
            roborazziComposeOptions = RoborazziComposeOptions {
                size(widthDp = device.widthDp, heightDp = device.heightDp)
            },
        ) {
            SolidVerdantTheme(themeMode = theme.mode) {
                AndroidShell(content)
            }
        }
    }
}
