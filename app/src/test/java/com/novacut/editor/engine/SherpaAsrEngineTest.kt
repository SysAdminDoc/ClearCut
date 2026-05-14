package com.novacut.editor.engine

import com.novacut.editor.engine.whisper.SherpaAsrEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaAsrEngineTest {

    @Test
    fun englishDefaultsToMoonshineV2Tiny() {
        val model = SherpaAsrEngine.preferredModelFor("en-US")

        assertEquals(SherpaAsrEngine.ModelVariant.MOONSHINE_V2_TINY_EN, model)
        assertEquals("moonshine-v2-tiny-en", model.modelPackageName)
        assertTrue(model.isMoonshineV2)
    }

    @Test
    fun nonEnglishFallsBackToMultilingualWhisper() {
        val model = SherpaAsrEngine.preferredModelFor("ja")

        assertEquals(SherpaAsrEngine.ModelVariant.WHISPER_TINY_MULTILINGUAL, model)
    }

    @Test
    fun releaseTargetIsPinnedAboveMoonshineV2Minimum() {
        assertEquals("1.13.2", SherpaAsrEngine.TARGET_SHERPA_ONNX_VERSION)
        assertEquals("1.12.28", SherpaAsrEngine.MIN_MOONSHINE_V2_SHERPA_VERSION)
        assertEquals(
            "sherpa-onnx-1.13.2.aar",
            SherpaAsrEngine.ANDROID_AAR_ASSET_NAME
        )
        assertTrue(SherpaAsrEngine.ANDROID_AAR_DOWNLOAD_URL.endsWith("/sherpa-onnx-1.13.2.aar"))
    }
}
