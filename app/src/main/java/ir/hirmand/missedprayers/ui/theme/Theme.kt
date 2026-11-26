package ir.hirmand.missedprayers.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import ir.hirmand.missedprayers.R

private val Emerald = Color(0xFF174D40)
private val DeepEmerald = Color(0xFF0D372F)
private val Gold = Color(0xFFC6A45B)
private val WarmCream = Color(0xFFFAF7F0)
private val Ink = Color(0xFF202A26)

private val PersianFontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)
private val BaseTypography = Typography()
private val AppTypography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = PersianFontFamily),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = PersianFontFamily),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = PersianFontFamily),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = PersianFontFamily),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = PersianFontFamily),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = PersianFontFamily),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = PersianFontFamily),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = PersianFontFamily),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = PersianFontFamily),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = PersianFontFamily),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = PersianFontFamily),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = PersianFontFamily),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = PersianFontFamily),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = PersianFontFamily),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = PersianFontFamily)
)

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
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, typography = AppTypography, content = content)
    }
}
