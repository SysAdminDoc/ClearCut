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
}
