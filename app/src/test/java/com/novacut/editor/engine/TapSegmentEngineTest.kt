package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapSegmentEngineTest {

    @Test
    fun sam21TinyIsTheDefaultTrackedMaskTarget() {
        val model = TapSegmentEngine.DEFAULT_ON_DEVICE_MODEL

        assertEquals(TapSegmentEngine.ModelVariant.SAM2_1_HIERA_TINY_ONNX, model)
        assertEquals(TapSegmentEngine.ModelFamily.SAM2_1, model.family)
        assertEquals("onnx-community/sam2.1-hiera-tiny-ONNX", model.modelPackageName)
        assertTrue(model.supportsVideoPropagation)
    }

    @Test
    fun sam21TinyRequiresPremiumTier() {
        val model = TapSegmentEngine.ModelVariant.SAM2_1_HIERA_TINY_ONNX

        assertTrue(model.workingSetBytes > TapSegmentEngine.PREMIUM_WORKING_SET_THRESHOLD_BYTES)
        assertTrue(model.requiresPremiumTier)
        assertFalse(model.canRunOnDevice(4_096))
        assertTrue(model.canRunOnDevice(6_144))
    }

    @Test
    fun recommendationFallsBackWhenPremiumModelsAreNotAllowed() {
        assertEquals(
            TapSegmentEngine.ModelVariant.MOBILE_SAM_ONNX,
            TapSegmentEngine.recommendedModelForDevice(
                availableRamMb = 12_288,
                allowPremiumModels = false
            )
        )
    }

    @Test
    fun recommendationUsesSam21OnPremiumDevice() {
        assertEquals(
            TapSegmentEngine.ModelVariant.SAM2_1_HIERA_TINY_ONNX,
            TapSegmentEngine.recommendedModelForDevice(
                availableRamMb = 8_192,
                allowPremiumModels = true
            )
        )
    }

    // --- R6.4 SAM 3 placeholder ---

    @Test
    fun sam3PlaceholderEnumRowExistsButIsNotRecommended() {
        // The placeholder row must exist so callers and API contracts are
        // forward-compatible, but it must not be selected by the recommendation
        // policy until SAM3_PLACEHOLDER_ENABLED is flipped on.
        val sam3 = TapSegmentEngine.ModelVariant.SAM3_HIERA_TINY_ONNX_PLACEHOLDER
        assertEquals(TapSegmentEngine.ModelFamily.SAM3, sam3.family)
        assertTrue(sam3.supportsVideoPropagation)
        assertTrue(sam3.requiresPremiumTier)

        assertFalse(TapSegmentEngine.SAM3_PLACEHOLDER_ENABLED)

        // Even on a maxed-out device with premium models allowed, the recommender
        // must stay on SAM 2.1 while the placeholder flag is off.
        assertEquals(
            TapSegmentEngine.ModelVariant.SAM2_1_HIERA_TINY_ONNX,
            TapSegmentEngine.recommendedModelForDevice(
                availableRamMb = 16_384,
                allowPremiumModels = true
            )
        )
    }

    @Test
    fun sam3SourceUrlIsRecorded() {
        // Watch item URL — track at compile time so a regression that drops the
        // SAM 3 metadata is caught immediately.
        assertEquals(
            "https://github.com/facebookresearch/sam3",
            TapSegmentEngine.SAM3_SOURCE_URL
        )
    }
}
