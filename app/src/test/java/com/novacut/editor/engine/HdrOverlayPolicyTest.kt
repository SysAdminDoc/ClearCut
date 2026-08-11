package com.novacut.editor.engine

import com.novacut.editor.model.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HdrOverlayPolicyTest {

    @Test
    fun strokedAndUnmappedBitmapOverlaysForceTheSdrOverlayPath() {
        val decision = HdrOverlayPolicy.evaluate(
            hdrRequested = true,
            codec = VideoCodec.HEVC,
            overlays = HdrOverlaySummary(
                textOverlayCount = 2,
                strokedTextOverlayCount = 1,
                imageOverlayCount = 1,
                watermarkPresent = true,
            ),
        )

        assertFalse(decision.preserveHdr)
        assertTrue(decision.requiresSdrFallback)
        assertNotNull(decision.disclosure)
        assertTrue(requireNotNull(decision.disclosure).contains("stroked text overlay"))
        assertTrue(requireNotNull(decision.disclosure).contains("image overlay"))
        assertTrue(requireNotNull(decision.disclosure).contains("watermark"))
    }

    @Test
    fun nativeTextAndGainMappedBitmapsRemainOnHdr() {
        val decision = HdrOverlayPolicy.evaluate(
            hdrRequested = true,
            codec = VideoCodec.AV1,
            apiLevel = 34,
            overlays = HdrOverlaySummary(
                textOverlayCount = 2,
                imageOverlayCount = 1,
                gainMappedImageOverlayCount = 1,
                watermarkPresent = true,
                watermarkHasGainMap = true,
            ),
        )

        assertTrue(decision.preserveHdr)
        assertFalse(decision.requiresSdrFallback)
        assertFalse(decision.samplerBudgetExceeded)
        assertEquals(6, decision.samplerCount)
        assertTrue(decision.disclosure == null)
    }

    @Test
    fun gainMappedBitmapsStillFallbackBeforeAndroid14() {
        val decision = HdrOverlayPolicy.evaluate(
            hdrRequested = true,
            codec = VideoCodec.HEVC,
            apiLevel = 33,
            overlays = HdrOverlaySummary(
                imageOverlayCount = 1,
                gainMappedImageOverlayCount = 1,
                watermarkPresent = true,
                watermarkHasGainMap = true,
            ),
        )

        assertFalse(decision.preserveHdr)
        assertTrue(decision.requiresSdrFallback)
        assertTrue(requireNotNull(decision.disclosure).contains("image overlay"))
        assertTrue(requireNotNull(decision.disclosure).contains("watermark"))
    }

    @Test
    fun hdrWithoutBitmapOverlaysRemainsEnabled() {
        val decision = HdrOverlayPolicy.evaluate(
            hdrRequested = true,
            codec = VideoCodec.AV1,
        )

        assertTrue(decision.preserveHdr)
        assertFalse(decision.requiresSdrFallback)
        assertTrue(decision.disclosure == null)
    }

    @Test
    fun disabledHdrRequestNeverAddsAnOverlayWarning() {
        val decision = HdrOverlayPolicy.evaluate(
            hdrRequested = false,
            codec = VideoCodec.HEVC,
            overlays = HdrOverlaySummary(textOverlayCount = 1),
        )

        assertFalse(decision.preserveHdr)
        assertFalse(decision.requiresSdrFallback)
        assertTrue(decision.disclosure == null)
    }

    @Test
    fun h264UsesExistingSdrCodecPolicyWithoutOverlaySpecificDisclosure() {
        val decision = HdrOverlayPolicy.evaluate(
            hdrRequested = true,
            codec = VideoCodec.H264,
            overlays = HdrOverlaySummary(imageOverlayCount = 1),
        )

        assertFalse(decision.preserveHdr)
        assertFalse(decision.requiresSdrFallback)
        assertTrue(decision.disclosure == null)
    }

    @Test
    fun hdrSamplerBudgetGetsANamedDecisionBeforeMedia3BuildsGl() {
        val decision = HdrOverlayPolicy.evaluate(
            hdrRequested = true,
            codec = VideoCodec.HEVC,
            apiLevel = 34,
            overlays = HdrOverlaySummary(
                imageOverlayCount = 8,
                gainMappedImageOverlayCount = 8,
            ),
        )

        assertFalse(decision.preserveHdr)
        assertFalse(decision.requiresSdrFallback)
        assertTrue(decision.samplerBudgetExceeded)
        assertEquals(MAX_HDR_OVERLAY_SAMPLERS + 1, decision.samplerCount)
        assertTrue(requireNotNull(decision.disclosure).contains("sampler budget"))
    }
}
