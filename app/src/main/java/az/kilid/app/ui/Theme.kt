package az.kilid.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import az.kilid.app.data.ThemeMode

/** Açıq fon, tünd göy əsas rəng, mavi aksent. */
object KilidColors {
    val Navy = Color(0xFF0B2545)
    val Accent = Color(0xFF1E6FFF)
    val AccentDark = Color(0xFF5B9BFF)
    val Success = Color(0xFF1A7F55)
    val SuccessDark = Color(0xFF4CD39A)
    val Warning = Color(0xFFB45309)
    val WarningDark = Color(0xFFF5B25C)
}

private val LightScheme = lightColorScheme(
    primary = KilidColors.Accent,
    onPrimary = Color.White,
    secondary = KilidColors.Navy,
    background = Color(0xFFF4F7FD),
    onBackground = KilidColors.Navy,
    surface = Color.White,
    onSurface = KilidColors.Navy,
    surfaceVariant = Color(0xFFE6EDFB),
    onSurfaceVariant = Color(0xFF3B4A66),
    error = Color(0xFFB3261E)
)

private val DarkScheme = darkColorScheme(
    primary = KilidColors.AccentDark,
    onPrimary = Color(0xFF00205C),
    secondary = Color(0xFFE4EDFF),
    background = Color(0xFF0A1220),
    onBackground = Color(0xFFE4EDFF),
    surface = Color(0xFF13203A),
    onSurface = Color(0xFFE4EDFF),
    surfaceVariant = Color(0xFF1D2D4D),
    onSurfaceVariant = Color(0xFFB7C4DF),
    error = Color(0xFFFF8A80)
)

@Composable
fun KilidTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}

@Composable
fun successColor(): Color = if (MaterialTheme.colorScheme.background.red < 0.5f) KilidColors.SuccessDark else KilidColors.Success

@Composable
fun warningColor(): Color = if (MaterialTheme.colorScheme.background.red < 0.5f) KilidColors.WarningDark else KilidColors.Warning
