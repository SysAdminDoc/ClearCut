package com.novacut.editor.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Catppuccin Mocha palette
object Mocha {
    val Midnight = Color(0xFF090B12)
    val Crust = Color(0xFF11111B)
    val Mantle = Color(0xFF181825)
    val Base = Color(0xFF1E1E2E)
    val Panel = Color(0xFF151826)
    val PanelRaised = Color(0xFF1B2031)
    val PanelHighest = Color(0xFF232A3E)
    val CardStroke = Color(0xFF2E354D)
    val CardStrokeStrong = Color(0xFF3B4360)
    val Glow = Color(0x66CBA6F7)
    val GlowSoft = Color(0x3389B4FA)
    val GlowWarm = Color(0x33F5E0DC)
    val Surface0 = Color(0xFF313244)
    val Surface1 = Color(0xFF45475A)
    val Surface2 = Color(0xFF585B70)
    val Overlay0 = Color(0xFF6C7086)
    val Overlay1 = Color(0xFF7F849C)
    val Overlay2 = Color(0xFF9399B2)
    val Subtext0 = Color(0xFFA6ADC8)
    val Subtext1 = Color(0xFFBAC2DE)
    val Text = Color(0xFFCDD6F4)
    val Lavender = Color(0xFFB4BEFE)
    val Blue = Color(0xFF89B4FA)
    val Sapphire = Color(0xFF74C7EC)
    val Sky = Color(0xFF89DCEB)
    val Teal = Color(0xFF94E2D5)
    val Green = Color(0xFFA6E3A1)
    val Yellow = Color(0xFFF9E2AF)
    val Peach = Color(0xFFFAB387)
    val Maroon = Color(0xFFEBA0AC)
    val Red = Color(0xFFF38BA8)
    val Mauve = Color(0xFFCBA6F7)
    val Pink = Color(0xFFF5C2E7)
    val Flamingo = Color(0xFFF2CDCD)
    val Rosewater = Color(0xFFF5E0DC)
}

private val NovaCutColorScheme = darkColorScheme(
    primary = Mocha.Mauve,
    onPrimary = Mocha.Crust,
    primaryContainer = Mocha.Mauve.copy(alpha = 0.3f),
    onPrimaryContainer = Mocha.Mauve,
    secondary = Mocha.Sapphire,
    onSecondary = Mocha.Crust,
    secondaryContainer = Mocha.Sapphire.copy(alpha = 0.24f),
    onSecondaryContainer = Mocha.Sky,
    tertiary = Mocha.Rosewater,
    onTertiary = Mocha.Crust,
    tertiaryContainer = Mocha.Rosewater.copy(alpha = 0.2f),
    onTertiaryContainer = Mocha.Rosewater,
    error = Mocha.Red,
    onError = Mocha.Crust,
    errorContainer = Mocha.Red.copy(alpha = 0.3f),
    onErrorContainer = Mocha.Red,
    background = Mocha.Midnight,
    onBackground = Mocha.Text,
    surface = Mocha.Panel,
    onSurface = Mocha.Text,
    surfaceVariant = Mocha.PanelRaised,
    onSurfaceVariant = Mocha.Subtext1,
    outline = Mocha.CardStrokeStrong,
    outlineVariant = Mocha.CardStroke,
    inverseSurface = Mocha.Text,
    inverseOnSurface = Mocha.Base,
    inversePrimary = Mocha.Lavender,
    surfaceDim = Mocha.Crust,
    surfaceBright = Mocha.PanelHighest,
    surfaceContainerLowest = Mocha.Midnight,
    surfaceContainerLow = Mocha.Panel,
    surfaceContainer = Mocha.Mantle,
    surfaceContainerHigh = Mocha.PanelRaised,
    surfaceContainerHighest = Mocha.PanelHighest
)

private val NovaCutTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 20.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 14.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.2.sp
    )
)

@Composable
fun NovaCutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NovaCutColorScheme,
        typography = NovaCutTypography,
        content = content
    )
}
