package com.novacut.editor.ui.editor

import com.novacut.editor.model.CaptionAccessibilityPreset
import com.novacut.editor.model.TextAnimation
import com.novacut.editor.model.isAccessibilityPreset
import com.novacut.editor.model.toCaptionStyleType
import com.novacut.editor.model.CaptionStyleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionStyleGalleryTest {

    @Test
    fun defaultTemplatesIncludeRequiredAccessibilityPresets() {
        val templates = defaultTemplates().filter { it.isAccessibilityPreset }

        assertTrue(templates.any { it.accessibilityPreset == CaptionAccessibilityPreset.WCAG_AA_CONTRAST })
        assertTrue(templates.any { it.accessibilityPreset == CaptionAccessibilityPreset.LARGE_TEXT })
        assertTrue(templates.any { it.accessibilityPreset == CaptionAccessibilityPreset.REDUCED_MOTION })
    }

    @Test
    fun accessibleTemplatesMeetCaptionReadabilityFloor() {
        val accessibleTemplates = defaultTemplates().filter { it.isAccessibilityPreset }

        assertTrue(accessibleTemplates.isNotEmpty())
        accessibleTemplates.forEach { template ->
            assertTrue("Accessible caption text must be at least 24sp", template.fontSize >= 24f)
            assertTrue(
                "Accessible caption text/background contrast must meet WCAG AA",
                contrastRatio(template.textColor, template.backgroundColor) >= 4.5
            )
            assertTrue("Accessible caption presets need an outline stroke", template.outlineWidth >= 2f)
        }
    }

    @Test
    fun reducedMotionPresetDisablesMotionStyles() {
        val reducedMotion = defaultTemplates()
            .single { it.accessibilityPreset == CaptionAccessibilityPreset.REDUCED_MOTION }

        assertEquals(TextAnimation.NONE, reducedMotion.animation)
        assertEquals(CaptionStyleType.SUBTITLE_BAR, reducedMotion.toCaptionStyleType())
        assertTrue(!reducedMotion.wordByWord)
    }

    @Test
    fun largeTextPresetStaysAbove1080pMinimum() {
        val largeText = defaultTemplates()
            .single { it.accessibilityPreset == CaptionAccessibilityPreset.LARGE_TEXT }

        assertTrue(largeText.fontSize >= 36f)
        assertEquals(TextAnimation.NONE, largeText.animation)
    }

    private fun contrastRatio(foreground: Long, background: Long): Double {
        val fg = relativeLuminance(foreground)
        val bg = relativeLuminance(background)
        val lighter = maxOf(fg, bg)
        val darker = minOf(fg, bg)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Long): Double {
        val r = linearizedChannel((color shr 16 and 0xFF).toInt())
        val g = linearizedChannel((color shr 8 and 0xFF).toInt())
        val b = linearizedChannel((color and 0xFF).toInt())
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun linearizedChannel(channel: Int): Double {
        val srgb = channel / 255.0
        return if (srgb <= 0.03928) {
            srgb / 12.92
        } else {
            Math.pow((srgb + 0.055) / 1.055, 2.4)
        }
    }
}
