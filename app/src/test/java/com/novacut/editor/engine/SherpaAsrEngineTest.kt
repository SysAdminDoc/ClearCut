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

    // --- R6.8 three-target policy ---

    @Test
    fun englishIgnoresPremiumTierAndUsesMoonshine() {
        // English always uses Moonshine v2 Tiny — the premium gate is multilingual-only.
        val model = SherpaAsrEngine.preferredModelFor(
            language = "en",
            allowPremiumModels = true,
            availableRamMb = 12_000,
        )
        assertEquals(SherpaAsrEngine.ModelVariant.MOONSHINE_V2_TINY_EN, model)
    }

    @Test
    fun multilingualWithoutPremiumStaysOnWhisperTiny() {
        val model = SherpaAsrEngine.preferredModelFor(
            language = "ja",
            allowPremiumModels = false,
            availableRamMb = 12_000,
        )
        assertEquals(SherpaAsrEngine.ModelVariant.WHISPER_TINY_MULTILINGUAL, model)
    }

    @Test
    fun multilingualWithPremiumButLowRamStaysOnWhisperTiny() {
        val model = SherpaAsrEngine.preferredModelFor(
            language = "ja",
            allowPremiumModels = true,
            availableRamMb = 4_096, // below the 6_144 floor for Turbo
        )
        assertEquals(SherpaAsrEngine.ModelVariant.WHISPER_TINY_MULTILINGUAL, model)
    }

    @Test
    fun multilingualPremiumOnHighRamPicksWhisperLargeV3Turbo() {
        val model = SherpaAsrEngine.preferredModelFor(
            language = "de",
            allowPremiumModels = true,
            availableRamMb = 8_192,
        )
        assertEquals(SherpaAsrEngine.ModelVariant.WHISPER_LARGE_V3_TURBO_MULTILINGUAL, model)
        assertTrue(model.requiresPremiumTier)
        assertTrue(model.isMultilingual)
        assertEquals(6_144, model.minimumRamMb)
    }

    @Test
    fun premiumMultilingualModelExportsExpectedMetadata() {
        val premium = SherpaAsrEngine.PREMIUM_MULTILINGUAL_MODEL
        assertEquals(SherpaAsrEngine.ModelVariant.WHISPER_LARGE_V3_TURBO_MULTILINGUAL, premium)
        assertEquals("Whisper Large V3 Turbo", premium.displayName)
        assertEquals("whisper-large-v3-turbo", premium.modelPackageName)
        assertEquals(800, premium.sizeMb)
        assertTrue(premium.isMultilingual)
        assertTrue(premium.requiresPremiumTier)
    }
}
