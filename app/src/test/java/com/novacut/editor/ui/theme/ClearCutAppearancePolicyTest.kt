package com.novacut.editor.ui.theme

import com.novacut.editor.engine.AppearanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClearCutAppearancePolicyTest {

    @Test
    fun systemModeUsesDarkCanvasUntilLightPaletteIsAudited() {
        assertEquals(
            AppearanceMode.DARK,
            ClearCutThemeDefaults.resolveMode(AppearanceMode.SYSTEM, systemDark = false),
        )
        assertEquals(
            AppearanceMode.DARK,
            ClearCutThemeDefaults.resolveMode(AppearanceMode.SYSTEM, systemDark = true),
        )
    }

    @Test
    fun highContrastSemanticTextMeetsWcagAa() {
        val colors = ClearCutThemeDefaults.colorsFor(AppearanceMode.HIGH_CONTRAST_DARK)

        listOf(
            colors.background,
            colors.backgroundMid,
            colors.panel,
            colors.panelRaised,
            colors.panelHighest,
            colors.surfaceBase,
            colors.surfaceLow,
            colors.surface,
            colors.surfaceHigh,
        ).forEach { surface ->
            assertTrue(ClearCutThemeDefaults.contrastRatio(colors.text, surface) >= 4.5)
            assertTrue(ClearCutThemeDefaults.contrastRatio(colors.subtext, surface) >= 4.5)
        }
        assertTrue(ClearCutThemeDefaults.contrastRatio(colors.disabledText, colors.disabledSurface) >= 4.5)
    }

    @Test
    fun highContrastSemanticStrokesMeetNonTextFloor() {
        val colors = ClearCutThemeDefaults.colorsFor(AppearanceMode.HIGH_CONTRAST_DARK)

        assertTrue(ClearCutThemeDefaults.contrastRatio(colors.cardStroke, colors.panel) >= 3.0)
        assertTrue(ClearCutThemeDefaults.contrastRatio(colors.cardStrokeStrong, colors.panel) >= 3.0)
        assertTrue(ClearCutThemeDefaults.contrastRatio(colors.cardStrokeStrong, colors.panelHighest) >= 3.0)
    }

    @Test
    fun lowEmphasisMochaTokensStayBelowSemanticIndicatorFloor() {
        val overlayOnPanel = ClearCutThemeDefaults.contrastRatio(Mocha.Overlay0, Mocha.PanelHighest)
        val strokeOnPanel = ClearCutThemeDefaults.contrastRatio(Mocha.CardStrokeStrong, Mocha.Panel)
        val highContrast = ClearCutThemeDefaults.colorsFor(AppearanceMode.HIGH_CONTRAST_DARK)

        assertTrue(overlayOnPanel < 3.0)
        assertTrue(strokeOnPanel < 3.0)
        assertTrue(ClearCutThemeDefaults.contrastRatio(highContrast.cardStrokeStrong, highContrast.panel) >= 3.0)
    }

    @Test
    fun selectedHighContrastChipHasReadableLabelForCommonAccents() {
        listOf(
            ClearCutAccents.Lavender,
            ClearCutAccents.Blue,
            ClearCutAccents.Sapphire,
            ClearCutAccents.Sky,
            ClearCutAccents.Teal,
            ClearCutAccents.Green,
            ClearCutAccents.Yellow,
            ClearCutAccents.Peach,
            ClearCutAccents.Maroon,
            ClearCutAccents.Red,
            ClearCutAccents.Mauve,
            ClearCutAccents.Pink,
            ClearCutAccents.Flamingo,
            ClearCutAccents.Rosewater,
        ).forEach { accent ->
            assertTrue(
                "Chip label contrast failed for $accent",
                ClearCutThemeDefaults.contrastRatio(
                    ClearCutThemeDefaults.colorsFor(AppearanceMode.HIGH_CONTRAST_DARK).onAccent,
                    accent,
                ) >= 4.5,
            )
        }
    }

    @Test
    fun highContrastIndicatorsMeetNonTextFloor() {
        val colors = ClearCutThemeDefaults.colorsFor(AppearanceMode.HIGH_CONTRAST_DARK)

        listOf(colors.overlay, colors.overlayStrong, colors.focusRing).forEach { indicator ->
            assertTrue(ClearCutThemeDefaults.contrastRatio(indicator, colors.panel) >= 3.0)
            assertTrue(ClearCutThemeDefaults.contrastRatio(indicator, colors.panelHighest) >= 3.0)
        }
    }

    @Test
    fun darkModeKeepsPrimaryTextReadable() {
        val colors = ClearCutThemeDefaults.colorsFor(AppearanceMode.DARK)

        assertTrue(ClearCutThemeDefaults.contrastRatio(colors.text, colors.panel) >= 4.5)
        assertTrue(ClearCutThemeDefaults.contrastRatio(colors.subtext, colors.panel) >= 4.5)
    }

    @Test
    fun radiusTokensStayWithinProfessionalGeometryCap() {
        listOf(Radius.xs, Radius.sm, Radius.md, Radius.lg, Radius.xl, Radius.xxl).forEach { radius ->
            assertTrue("Radius token exceeded 12dp: $radius", radius.value <= 12f)
        }
    }
}
