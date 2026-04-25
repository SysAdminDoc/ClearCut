package com.novacut.editor.engine

import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.Resolution
import com.novacut.editor.model.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportColorConfidenceEngineTest {

    @Test
    fun sdrExportReportsBroadCompatibility() {
        val report = ExportColorConfidenceEngine.analyze(
            config = ExportConfig(codec = VideoCodec.H264, hdr10PlusMetadata = false),
            width = 1920,
            height = 1080,
            hdrSupport = ExportColorConfidenceEngine.HdrEncodeSupport()
        )

        assertFalse(report.hasWarnings)
        assertEquals("SDR delivery", report.chips.first().label)
    }

    @Test
    fun h264HdrRequestWarnsAboutSdrCodec() {
        val report = ExportColorConfidenceEngine.analyze(
            config = ExportConfig(codec = VideoCodec.H264, hdr10PlusMetadata = true),
            width = 1920,
            height = 1080,
            hdrSupport = ExportColorConfidenceEngine.HdrEncodeSupport(setOf("HDR10+"))
        )

        assertTrue(report.hasWarnings)
        assertTrue(report.warnings.first().contains("H.264 cannot carry HDR"))
        assertEquals(ExportColorConfidenceEngine.Tone.WARNING, report.chips.first().tone)
    }

    @Test
    fun hevcHdr10PlusSupportReportsDynamicMetadata() {
        val report = ExportColorConfidenceEngine.analyze(
            config = ExportConfig(codec = VideoCodec.HEVC, hdr10PlusMetadata = true),
            width = 1920,
            height = 1080,
            hdrSupport = ExportColorConfidenceEngine.HdrEncodeSupport(
                supportedFormats = setOf("HDR10", "HDR10+"),
                maxWidth = 3840,
                maxHeight = 2160,
                maxBitrate = 120_000_000
            )
        )

        assertFalse(report.hasWarnings)
        assertTrue(report.chips.any { it.label == "HDR10+ metadata" })
    }

    @Test
    fun hdrRequestWarnsWhenDeviceDoesNotAdvertiseSupport() {
        val report = ExportColorConfidenceEngine.analyze(
            config = ExportConfig(codec = VideoCodec.HEVC, hdr10PlusMetadata = true),
            width = 1920,
            height = 1080,
            hdrSupport = ExportColorConfidenceEngine.HdrEncodeSupport()
        )

        assertTrue(report.hasWarnings)
        assertTrue(report.warnings.any { it.contains("does not advertise HDR encode support") })
    }

    @Test
    fun hdrRequestWarnsWhenExportExceedsAdvertisedHdrLimits() {
        val report = ExportColorConfidenceEngine.analyze(
            config = ExportConfig(
                resolution = Resolution.UHD_4K,
                codec = VideoCodec.HEVC,
                hdr10PlusMetadata = true
            ),
            width = 3840,
            height = 2160,
            hdrSupport = ExportColorConfidenceEngine.HdrEncodeSupport(
                supportedFormats = setOf("HDR10+"),
                maxWidth = 1920,
                maxHeight = 1080,
                maxBitrate = 40_000_000
            )
        )

        assertTrue(report.hasWarnings)
        assertTrue(report.warnings.any { it.contains("up to 1920x1080") })
        assertTrue(report.warnings.any { it.contains("bitrate is advertised up to 40 Mbps") })
    }
}
