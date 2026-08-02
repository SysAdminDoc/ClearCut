package com.novacut.editor.engine

import com.novacut.editor.model.VideoCodec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HdrOverlayPolicyTest {

    @Test
    fun textImageAndWatermarkOverlaysForceTheSdrOverlayPath() {
        val decision = HdrOverlayPolicy.evaluate(
            hdrRequested = true,
            codec = VideoCodec.HEVC,
            overlays = HdrOverlaySummary(
                textOverlayCount = 2,
                imageOverlayCount = 1,
                watermarkPresent = true,
            ),
        )

        assertFalse(decision.preserveHdr)
        assertTrue(decision.requiresSdrFallback)
        assertNotNull(decision.disclosure)
        assertTrue(requireNotNull(decision.disclosure).contains("text overlays"))
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
}
