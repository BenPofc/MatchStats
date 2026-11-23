package br.com.matchstats.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object MatchStatsColors {
    val Navy = Color(0xFF10243F)
    val Green = Color(0xFF078A30)
    val SoftGreen = Color(0xFFDDF8E5)
    val Background = Color(0xFFF7F8FA)
    val White = Color(0xFFFFFFFF)

    val Ink = Color(0xFF141D29)
    val Muted = Color(0xFF515C6B)
    val Border = Color(0xFFDDE3E9)
    val Outline = Color(0xFF7C8795)
    val Disabled = Color(0xFFE8ECF0)
    val Error = Color(0xFFB3261E)
    val GreenInk = Color(0xFF06591F)
    val ActionGreen = Color(0xFF077A2D)
    val BrightGreen = Color(0xFF32CE59)
    val BannerGreen = Color(0xFF14A43C)
    val BannerNavy = Color(0xFF152F4D)
    val OnNavyMuted = Color(0xFFDCE6F2)
}

object MatchStatsSpacing {
    val Tiny = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Screen = 16.dp
    val Large = 20.dp
    val ExtraLarge = 24.dp
    val ContentMax = 620.dp
}

private val Colors = lightColorScheme(
    primary = MatchStatsColors.Green,
    onPrimary = MatchStatsColors.White,
    primaryContainer = MatchStatsColors.SoftGreen,
    onPrimaryContainer = MatchStatsColors.GreenInk,
    secondary = MatchStatsColors.GreenInk,
    onSecondary = MatchStatsColors.White,
    secondaryContainer = MatchStatsColors.SoftGreen,
    onSecondaryContainer = MatchStatsColors.GreenInk,
    tertiary = MatchStatsColors.Navy,
    onTertiary = MatchStatsColors.White,
    background = MatchStatsColors.Background,
    onBackground = MatchStatsColors.Ink,
    surface = MatchStatsColors.White,
    onSurface = MatchStatsColors.Ink,
    surfaceVariant = MatchStatsColors.Background,
    onSurfaceVariant = MatchStatsColors.Muted,
    surfaceContainer = MatchStatsColors.Background,
    surfaceContainerHigh = MatchStatsColors.White,
    surfaceContainerHighest = MatchStatsColors.Disabled,
    outline = MatchStatsColors.Outline,
    outlineVariant = MatchStatsColors.Border,
    error = MatchStatsColors.Error,
)

private val Type = Typography(
    headlineMedium = TextStyle(fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

private val Corners = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MatchStatsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, shapes = Corners, content = content)
}
