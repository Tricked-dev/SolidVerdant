/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package dev.tricked.solidverdant.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.tricked.solidverdant.data.local.AppThemeMode

private val DarkColorScheme = darkColorScheme()
private val LightColorScheme = lightColorScheme()

private val VerdantLightColorScheme = lightColorScheme(
    primary = Color(0xFF386A20),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F397),
    onPrimaryContainer = Color(0xFF0C2000),
    secondary = Color(0xFF55624C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E7CB),
    onSecondaryContainer = Color(0xFF131F0D),
    tertiary = Color(0xFF386666),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBBEBEB),
    onTertiaryContainer = Color(0xFF002020),
    background = Color(0xFFFDFDF5),
    onBackground = Color(0xFF1A1C18),
    surface = Color(0xFFFDFDF5),
    onSurface = Color(0xFF1A1C18),
    surfaceVariant = Color(0xFFE0E4D8),
    onSurfaceVariant = Color(0xFF44483F),
    outline = Color(0xFF74796E),
)

private val NeoColorScheme = darkColorScheme(
    primary = Color(0xFF1EF3FF),
    onPrimary = Color(0xFF03050A),
    primaryContainer = Color(0xFF0D141F),
    onPrimaryContainer = Color(0xFF1EF3FF),
    secondary = Color(0xFFFCEE0A),
    onSecondary = Color(0xFF03050A),
    secondaryContainer = Color(0xFF242F16),
    onSecondaryContainer = Color(0xFFFCEE0A),
    tertiary = Color(0xFF4DFFB0),
    onTertiary = Color(0xFF03050A),
    tertiaryContainer = Color(0xFF0D1F1A),
    onTertiaryContainer = Color(0xFF4DFFB0),
    error = Color(0xFFFF1F6D),
    onError = Color(0xFF03050A),
    errorContainer = Color(0xFF3B0A1E),
    onErrorContainer = Color(0xFFFFB1C8),
    background = Color(0xFF03050A),
    onBackground = Color(0xFFF0F6FF),
    surface = Color(0xFF080D15),
    onSurface = Color(0xFFF0F6FF),
    surfaceVariant = Color(0xFF0D141F),
    onSurfaceVariant = Color(0xFF8298AE),
    surfaceDim = Color(0xFF03050A),
    surfaceBright = Color(0xFF182536),
    surfaceContainerLowest = Color(0xFF03050A),
    surfaceContainerLow = Color(0xFF080D15),
    surfaceContainer = Color(0xFF0D141F),
    surfaceContainerHigh = Color(0xFF111B29),
    surfaceContainerHighest = Color(0xFF182536),
    surfaceTint = Color(0xFF1EF3FF),
    outline = Color(0xFF3D5876),
    outlineVariant = Color(0xFF243549),
)

@Composable
fun SolidVerdantTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        themeMode == AppThemeMode.LIGHT -> VerdantLightColorScheme
        themeMode == AppThemeMode.NEO -> NeoColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)

            val useLightSystemBars = themeMode == AppThemeMode.LIGHT ||
                (themeMode == AppThemeMode.SYSTEM && !darkTheme)
            insetsController.isAppearanceLightStatusBars = useLightSystemBars
            insetsController.isAppearanceLightNavigationBars = useLightSystemBars
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
