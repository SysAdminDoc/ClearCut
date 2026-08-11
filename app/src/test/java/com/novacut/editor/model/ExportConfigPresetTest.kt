package com.novacut.editor.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportConfigPresetTest {

    @Test
    fun applyingPresetCopiesEveryDeliverySetting() {
        val config = ExportConfig().withPlatformPreset(PlatformPreset.TIKTOK)

        assertEquals(Resolution.FHD_1080P, config.resolution)
        assertEquals(AspectRatio.RATIO_9_16, config.aspectRatio)
        assertEquals(30, config.frameRate)
        assertEquals(VideoCodec.H264, config.codec)
        assertEquals(PlatformPreset.TIKTOK, config.platformPreset)
    }

    @Test
    fun selectedPresetWinsOverProjectCanvasForOutput() {
        val presetConfig = ExportConfig().withPlatformPreset(PlatformPreset.INSTAGRAM_FEED)
        val customConfig = ExportConfig()

        assertEquals(
            AspectRatio.RATIO_1_1,
            presetConfig.outputAspectRatio(AspectRatio.RATIO_16_9),
        )
        assertEquals(
            AspectRatio.RATIO_9_16,
            customConfig.outputAspectRatio(AspectRatio.RATIO_9_16),
        )
    }
}
