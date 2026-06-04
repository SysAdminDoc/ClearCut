package com.novacut.editor.ui.theme

import com.novacut.editor.engine.AppearanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovaCutAppearancePolicyTest {

    @Test
    fun systemModeUsesDarkCanvasUntilLightPaletteIsAudited() {
        assertEquals(
            AppearanceMode.DARK,
            NovaCutThemeDefaults.resolveMode(AppearanceMode.SYSTEM, systemDark = false),
        )
        assertEquals(
            AppearanceMode.DARK,
            NovaCutThemeDefaults.resolveMode(AppearanceMode.SYSTEM, systemDark = true),
        )
    }

    @Test
    fun highContrastSemanticTextMeetsWcagAa() {
        val colors = NovaCutThemeDefaults.colorsFor(AppearanceMode.HIGH_CONTRAST_DARK)

        assertTrue(NovaCutThemeDefaults.contrastRatio(colors.text, colors.panel) >= 4.5)
        assertTrue(NovaCutThemeDefaults.contrastRatio(colors.subtext, colors.panel) >= 4.5)
        assertTrue(NovaCutThemeDefaults.contrastRatio(colors.disabledText, colors.panel) >= 4.5)
    }

    @Test
    fun highContrastSemanticStrokesMeetNonTextFloor() {
        val colors = NovaCutThemeDefaults.colorsFor(AppearanceMode.HIGH_CONTRAST_DARK)

        assertTrue(NovaCutThemeDefaults.contrastRatio(colors.cardStroke, colors.panel) >= 3.0)
        assertTrue(NovaCutThemeDefaults.contrastRatio(colors.cardStrokeStrong, colors.panel) >= 3.0)
        assertTrue(NovaCutThemeDefaults.contrastRatio(colors.cardStrokeStrong, colors.panelHighest) >= 3.0)
    }

    @Test
    fun lowEmphasisMochaTokensStayBelowSemanticIndicatorFloor() {
        val overlayOnPanel = NovaCutThemeDefaults.contrastRatio(Mocha.Overlay0, Mocha.PanelHighest)
        val strokeOnPanel = NovaCutThemeDefaults.contrastRatio(Mocha.CardStrokeStrong, Mocha.Panel)
        val highContrast = NovaCutThemeDefaults.colorsFor(AppearanceMode.HIGH_CONTRAST_DARK)

        assertTrue(overlayOnPanel < 3.0)
        assertTrue(strokeOnPanel < 3.0)
        assertTrue(NovaCutThemeDefaults.contrastRatio(highContrast.cardStrokeStrong, highContrast.panel) >= 3.0)
    }

    @Test
    fun selectedHighContrastChipHasReadableLabelForCommonAccents() {
        listOf(
            Mocha.Mauve,
            Mocha.Sky,
            Mocha.Green,
            Mocha.Yellow,
            Mocha.Peach,
            Mocha.Red,
        ).forEach { accent ->
            assertTrue(
                "Chip label contrast failed for $accent",
                NovaCutThemeDefaults.contrastRatio(Mocha.Crust, accent) >= 4.5,
            )
        }
    }

    @Test
    fun darkModeKeepsPrimaryTextReadable() {
        val colors = NovaCutThemeDefaults.colorsFor(AppearanceMode.DARK)

        assertTrue(NovaCutThemeDefaults.contrastRatio(colors.text, colors.panel) >= 4.5)
        assertTrue(NovaCutThemeDefaults.contrastRatio(colors.subtext, colors.panel) >= 4.5)
    }
}
