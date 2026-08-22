package com.novacut.editor.engine

import com.novacut.editor.model.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncoderCapabilityProbeHdrTest {

    @Test
    fun hdrFeatureSupportRequiresEitherPlatformEditingFeature() {
        assertFalse(EncoderCapabilityProbe.HdrFeatureSupport().canPreserveHdr)
        assertTrue(
            EncoderCapabilityProbe.HdrFeatureSupport(hdrEditing = true).canPreserveHdr
        )
        assertTrue(
            EncoderCapabilityProbe.HdrFeatureSupport(hlgEditing = true).canPreserveHdr
        )
    }

    @Test
    fun advertisedFeatureNamesExposeThePlatformContracts() {
        val support = EncoderCapabilityProbe.HdrFeatureSupport(
            hdrEditing = true,
            hlgEditing = true,
        )

        assertEquals(
            setOf("FEATURE_HdrEditing", "FEATURE_HlgEditing"),
            support.advertisedFeatureNames,
        )
    }

    @Test
    fun profileNamesDoNotBypassTheStrictFeatureGate() {
        val support = EncoderCapabilityProbe.HdrProfileSupport(
            codec = VideoCodec.HEVC,
            supportedFormats = setOf(EncoderCapabilityProbe.HdrExportFormat.HDR10),
            featureSupport = EncoderCapabilityProbe.HdrFeatureSupport(),
        )

        assertTrue(support.hasAnyHdr)
        assertFalse(support.canPreserveHdr)
        assertTrue(support.featureFailureReason().contains("FEATURE_HdrEditing"))
    }
}
