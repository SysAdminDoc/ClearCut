package com.novacut.editor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.novacut.editor.engine.AppearanceMode

// Catppuccin Mocha palette
object Mocha {
    // Neutral creator-workspace layers. Catppuccin accents remain available for media
    // semantics, while persistent chrome stays graphite so footage remains the focal point.
    val Midnight = Color(0xFF03070A)
    val Crust = Color(0xFF071016)
    val Mantle = Color(0xFF0B141C)
    val Base = Color(0xFF0E1822)
    val Panel = Color(0xFF0A1219)
    val PanelRaised = Color(0xFF101B25)
    val PanelHighest = Color(0xFF16232F)
    val CardStroke = Color(0xFF263746)
    val CardStrokeStrong = Color(0xFF3A5366)
    val Glow = Color(0x66CBA6F7)
    val GlowSoft = Color(0x3389B4FA)
    val GlowWarm = Color(0x33F5E0DC)
    val Surface0 = Color(0xFF243240)
    val Surface1 = Color(0xFF3B4A58)
    val Surface2 = Color(0xFF566574)
    val Overlay0 = Color(0xFF626B78)
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

private val ClearCutDarkColorScheme = darkColorScheme(
    primary = Mocha.Sky,
    onPrimary = Mocha.Crust,
    primaryContainer = Mocha.Sky.copy(alpha = 0.18f),
    onPrimaryContainer = Mocha.Sky,
    secondary = Mocha.Peach,
    onSecondary = Mocha.Crust,
    secondaryContainer = Mocha.Peach.copy(alpha = 0.16f),
    onSecondaryContainer = Mocha.Peach,
    tertiary = Mocha.Mauve,
    onTertiary = Mocha.Crust,
    tertiaryContainer = Mocha.Mauve.copy(alpha = 0.16f),
    onTertiaryContainer = Mocha.Mauve,
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

private val ClearCutHighContrastColorScheme = darkColorScheme(
    primary = Mocha.Sky,
    onPrimary = Mocha.Crust,
    primaryContainer = Mocha.Sky,
    onPrimaryContainer = Mocha.Crust,
    secondary = Mocha.Green,
    onSecondary = Mocha.Crust,
    secondaryContainer = Mocha.Green,
    onSecondaryContainer = Mocha.Crust,
    tertiary = Mocha.Peach,
    onTertiary = Mocha.Crust,
    tertiaryContainer = Mocha.Peach,
    onTertiaryContainer = Mocha.Crust,
    error = Mocha.Red,
    onError = Mocha.Crust,
    errorContainer = Mocha.Red,
    onErrorContainer = Mocha.Crust,
    background = Color(0xFF05070D),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF070A12),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF111827),
    onSurfaceVariant = Color(0xFFF4F7FF),
    outline = Color(0xFFF4F7FF),
    outlineVariant = Color(0xFFBAC2DE),
    inverseSurface = Color(0xFFFFFFFF),
    inverseOnSurface = Color(0xFF05070D),
    inversePrimary = Mocha.Sky,
    surfaceDim = Color(0xFF05070D),
    surfaceBright = Color(0xFF172033),
    surfaceContainerLowest = Color(0xFF05070D),
    surfaceContainerLow = Color(0xFF070A12),
    surfaceContainer = Color(0xFF0C111D),
    surfaceContainerHigh = Color(0xFF111827),
    surfaceContainerHighest = Color(0xFF172033)
)

data class ClearCutSemanticColors(
    val mode: AppearanceMode,
    val highContrast: Boolean,
    val background: Color,
    val backgroundMid: Color,
    val panel: Color,
    val panelRaised: Color,
    val panelHighest: Color,
    val cardStroke: Color,
    val cardStrokeStrong: Color,
    val text: Color,
    val subtext: Color,
    val disabledText: Color,
    val accent: Color,
    val accentSecondary: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val focusRing: Color,
    val canvas: Color,
    val selectedSurface: Color,
    val disabledSurface: Color,
)

val LocalClearCutColors = staticCompositionLocalOf {
    ClearCutThemeDefaults.colorsFor(AppearanceMode.DARK)
}

object ClearCutThemeDefaults {
    fun resolveMode(mode: AppearanceMode, systemDark: Boolean): AppearanceMode = when (mode) {
        AppearanceMode.SYSTEM -> {
            // ClearCut deliberately keeps the editing canvas dark until a video-neutral
            // light palette has a full screenshot/contrast audit.
            if (systemDark) AppearanceMode.DARK else AppearanceMode.DARK
        }
        AppearanceMode.DARK -> AppearanceMode.DARK
        AppearanceMode.HIGH_CONTRAST_DARK -> AppearanceMode.HIGH_CONTRAST_DARK
    }

    fun colorSchemeFor(resolvedMode: AppearanceMode) = when (resolvedMode) {
        AppearanceMode.HIGH_CONTRAST_DARK -> ClearCutHighContrastColorScheme
        AppearanceMode.SYSTEM,
        AppearanceMode.DARK -> ClearCutDarkColorScheme
    }

    fun colorsFor(resolvedMode: AppearanceMode): ClearCutSemanticColors = when (resolvedMode) {
        AppearanceMode.HIGH_CONTRAST_DARK -> ClearCutSemanticColors(
            mode = resolvedMode,
            highContrast = true,
            background = Color(0xFF05070D),
            backgroundMid = Color(0xFF070A12),
            panel = Color(0xFF070A12),
            panelRaised = Color(0xFF0C111D),
            panelHighest = Color(0xFF172033),
            cardStroke = Color(0xFFBAC2DE),
            cardStrokeStrong = Color(0xFFF4F7FF),
            text = Color(0xFFFFFFFF),
            subtext = Color(0xFFF4F7FF),
            disabledText = Color(0xFFBAC2DE),
            accent = Mocha.Sky,
            accentSecondary = Mocha.Peach,
            success = Mocha.Green,
            warning = Mocha.Peach,
            danger = Mocha.Red,
            focusRing = Color(0xFFFFFFFF),
            canvas = Color(0xFF000000),
            selectedSurface = Mocha.Sky,
            disabledSurface = Color(0xFF111827),
        )
        AppearanceMode.SYSTEM,
        AppearanceMode.DARK -> ClearCutSemanticColors(
            mode = AppearanceMode.DARK,
            highContrast = false,
            background = Mocha.Midnight,
            backgroundMid = Mocha.Mantle,
            panel = Mocha.Panel,
            panelRaised = Mocha.PanelRaised,
            panelHighest = Mocha.PanelHighest,
            cardStroke = Mocha.CardStroke,
            cardStrokeStrong = Mocha.CardStrokeStrong,
            text = Mocha.Text,
            subtext = Mocha.Subtext0,
            disabledText = Mocha.Subtext0,
            accent = Mocha.Sky,
            accentSecondary = Mocha.Peach,
            success = Mocha.Green,
            warning = Mocha.Peach,
            danger = Mocha.Red,
            focusRing = Mocha.Sky,
            canvas = Color(0xFF000000),
            selectedSurface = Mocha.Sky.copy(alpha = 0.14f),
            disabledSurface = Mocha.PanelHighest.copy(alpha = 0.48f),
        )
    }

    fun contrastRatio(foreground: Color, background: Color): Double {
        val fg = relativeLuminance(foreground)
        val bg = relativeLuminance(background)
        val lighter = maxOf(fg, bg)
        val darker = minOf(fg, bg)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        val r = linearizedChannel(color.red)
        val g = linearizedChannel(color.green)
        val b = linearizedChannel(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun linearizedChannel(channel: Float): Double {
        val srgb = channel.toDouble()
        return if (srgb <= 0.03928) {
            srgb / 12.92
        } else {
            Math.pow((srgb + 0.055) / 1.055, 2.4)
        }
    }
}

private val ClearCutTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
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

private val ClearCutShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.xl),
    extraLarge = RoundedCornerShape(Radius.xxl),
)

@Composable
fun ClearCutTheme(
    appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val resolvedMode = ClearCutThemeDefaults.resolveMode(
        mode = appearanceMode,
        systemDark = isSystemInDarkTheme(),
    )
    CompositionLocalProvider(LocalClearCutColors provides ClearCutThemeDefaults.colorsFor(resolvedMode)) {
        MaterialTheme(
            colorScheme = ClearCutThemeDefaults.colorSchemeFor(resolvedMode),
            typography = ClearCutTypography,
            shapes = ClearCutShapes,
            content = content
        )
    }
}
