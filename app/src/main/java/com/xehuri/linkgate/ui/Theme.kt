package com.xehuri.linkgate.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6FEB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF0B2B5C),
    secondary = Color(0xFF3F5F8F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E3F8),
    onSecondaryContainer = Color(0xFF11233D),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF12151A),
    surface = Color(0xFFFBFCFE),
    onSurface = Color(0xFF12151A),
    surfaceVariant = Color(0xFFE6EAF2),
    onSurfaceVariant = Color(0xFF5A6170),
    outline = Color(0xFF8A93A3),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC2FF),
    onPrimary = Color(0xFF06234D),
    primaryContainer = Color(0xFF17386B),
    onPrimaryContainer = Color(0xFFD9E6FF),
    secondary = Color(0xFFB4C7E7),
    onSecondary = Color(0xFF122033),
    secondaryContainer = Color(0xFF28384F),
    onSecondaryContainer = Color(0xFFD8E3F8),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE4E7EE),
    surface = Color(0xFF141922),
    onSurface = Color(0xFFE4E7EE),
    surfaceVariant = Color(0xFF232A36),
    onSurfaceVariant = Color(0xFFAEB6C4),
    outline = Color(0xFF6C7585),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF3B0906),
)

/** 主题跟随系统深浅色，没有任何手动切换开关（用户要求「夜间模式跟随系统」）。 */
@Composable
fun LinkGateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
