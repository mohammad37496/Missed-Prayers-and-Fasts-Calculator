package ir.hirmand.missedprayers.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

private val Emerald = Color(0xFF174D40)
private val DeepEmerald = Color(0xFF0D372F)
private val Gold = Color(0xFFC6A45B)
private val WarmCream = Color(0xFFFAF7F0)
private val Ink = Color(0xFF202A26)

private val LightColors = lightColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    secondary = Gold,
    onSecondary = DeepEmerald,
    background = WarmCream,
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BCDBB),
    secondary = Color(0xFFE0C17B),
    background = Color(0xFF111B17),
    surface = Color(0xFF1A2822)
)

@Composable
fun MissedWorshipTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
