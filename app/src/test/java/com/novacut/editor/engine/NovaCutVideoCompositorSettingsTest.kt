package com.novacut.editor.engine

import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import com.novacut.editor.model.BlendMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@androidx.annotation.OptIn(UnstableApi::class)
class NovaCutVideoCompositorSettingsTest {

    @Test
    fun getOutputSize_usesExportTargetSize() {
        val settings = NovaCutVideoCompositorSettings(
            outputWidth = 1920,
            outputHeight = 1080,
            layers = emptyList()
        )

        val size = settings.getOutputSize(mutableListOf(Size(640, 360)))

        assertEquals(1920, size.width)
        assertEquals(1080, size.height)
    }

    @Test
    fun getOutputSize_guardsInvalidTargetSize() {
        val settings = NovaCutVideoCompositorSettings(
            outputWidth = 0,
            outputHeight = -1,
            layers = emptyList()
        )

        val size = settings.getOutputSize(mutableListOf())

        assertEquals(1, size.width)
        assertEquals(1, size.height)
    }

    @Test
    fun getOverlaySettings_mapsTrackOpacityByInputId() {
        val settings = NovaCutVideoCompositorSettings(
            outputWidth = 1920,
            outputHeight = 1080,
            layers = listOf(
                NovaCutCompositorLayer(
                    inputId = 0,
                    trackId = "base",
                    trackIndex = 0,
                    opacity = 1f,
                    blendMode = BlendMode.NORMAL
                ),
                NovaCutCompositorLayer(
                    inputId = 1,
                    trackId = "overlay",
                    trackIndex = 1,
                    opacity = 0.35f,
                    blendMode = BlendMode.MULTIPLY
                )
            )
        )

        assertEquals(1f, settings.getOverlaySettings(0, 0L).getAlphaScale(), 0.0001f)
        assertEquals(0.35f, settings.getOverlaySettings(1, 0L).getAlphaScale(), 0.0001f)
        assertEquals(BlendMode.MULTIPLY, settings.layerForInput(1)?.blendMode)
    }

    @Test
    fun getOverlaySettings_clampsUnsafeOpacityAndDefaultsUnknownInput() {
        val settings = NovaCutVideoCompositorSettings(
            outputWidth = 1920,
            outputHeight = 1080,
            layers = listOf(
                NovaCutCompositorLayer(
                    inputId = 0,
                    trackId = "bad",
                    trackIndex = 0,
                    opacity = Float.NaN,
                    blendMode = BlendMode.NORMAL
                ),
                NovaCutCompositorLayer(
                    inputId = 1,
                    trackId = "too-high",
                    trackIndex = 1,
                    opacity = 2f,
                    blendMode = BlendMode.NORMAL
                ),
                NovaCutCompositorLayer(
                    inputId = 2,
                    trackId = "too-low",
                    trackIndex = 2,
                    opacity = -1f,
                    blendMode = BlendMode.NORMAL
                )
            )
        )

        assertEquals(1f, settings.getOverlaySettings(0, 0L).getAlphaScale(), 0.0001f)
        assertEquals(1f, settings.getOverlaySettings(1, 0L).getAlphaScale(), 0.0001f)
        assertEquals(0f, settings.getOverlaySettings(2, 0L).getAlphaScale(), 0.0001f)
        assertEquals(1f, settings.getOverlaySettings(99, 0L).getAlphaScale(), 0.0001f)
        assertNull(settings.layerForInput(99))
    }
}
