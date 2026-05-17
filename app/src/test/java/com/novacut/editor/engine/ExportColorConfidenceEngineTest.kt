package com.novacut.editor.engine

import android.net.FakeUri
import android.net.SecondFakeUri
import android.net.Uri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.Resolution
import com.novacut.editor.model.SourceColorMetadata
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
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
    fun sdrExportReportsUltraHdrSourceWithoutWarning() {
        val report = ExportColorConfidenceEngine.analyze(
            config = ExportConfig(codec = VideoCodec.HEVC, hdr10PlusMetadata = false),
            width = 1920,
            height = 1080,
            hdrSupport = ExportColorConfidenceEngine.HdrEncodeSupport(setOf("HDR10+")),
            sourceSummary = ExportColorConfidenceEngine.SourceHdrSummary(
                supportedFormats = setOf("Ultra HDR gain map"),
                inspectedSourceCount = 1,
                totalSourceCount = 1
            )
        )

        assertFalse(report.hasWarnings)
        assertTrue(report.chips.any { it.label == "Ultra HDR source" })
        assertTrue(report.chips.any { it.detail.contains("Preserve HDR Metadata") })
    }

    @Test
    fun sdrExportReportsApvSourceChipWithoutWarningList() {
        val report = ExportColorConfidenceEngine.analyze(
            config = ExportConfig(codec = VideoCodec.HEVC, hdr10PlusMetadata = false),
            width = 1920,
            height = 1080,
            hdrSupport = ExportColorConfidenceEngine.HdrEncodeSupport(),
            sourceSummary = ExportColorConfidenceEngine.SourceHdrSummary(
                inspectedSourceCount = 1,
                totalSourceCount = 1,
                apvSourceCount = 1
            )
        )

        assertFalse(report.hasWarnings)
        assertTrue(report.chips.any { chip ->
            chip.label == "Source is APV" &&
                chip.detail.contains("very large source files") &&
                chip.tone == ExportColorConfidenceEngine.Tone.WARNING
        })
    }

    @Test
    fun summarizeSourcesCountsDistinctApvSources() {
        val summary = ExportColorConfidenceEngine.summarizeSources(
            listOf(
                Track(
                    id = "video-1",
                    type = TrackType.VIDEO,
                    index = 0,
                    clips = listOf(
                        clipWithMime(FakeUri, EncoderCapabilityProbe.MIME_APV),
                        clipWithMime(FakeUri, EncoderCapabilityProbe.MIME_APV),
                        clipWithMime(SecondFakeUri, "video/hevc")
                    )
                )
            )
        )

        assertEquals(2, summary.inspectedSourceCount)
        assertEquals(2, summary.totalSourceCount)
        assertEquals(1, summary.apvSourceCount)
        assertTrue(summary.hasApvSource)
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
    fun av1DolbyVisionProfile10SupportReportsDynamicPath() {
        val report = ExportColorConfidenceEngine.analyze(
            config = ExportConfig(codec = VideoCodec.AV1, hdr10PlusMetadata = true),
            width = 1920,
            height = 1080,
            hdrSupport = ExportColorConfidenceEngine.HdrEncodeSupport(
                supportedFormats = setOf("HDR10", "Dolby Vision Profile 10"),
                maxWidth = 3840,
                maxHeight = 2160,
                maxBitrate = 120_000_000
            )
        )

        assertFalse(report.hasWarnings)
        assertTrue(report.chips.any { it.label == "Dolby Vision path" })
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

    private fun clipWithMime(uri: Uri, mimeType: String): Clip {
        return Clip(
            id = uri.toString(),
            sourceUri = uri,
            sourceDurationMs = 1_000L,
            timelineStartMs = 0L,
            sourceColorMetadata = SourceColorMetadata(
                mimeType = mimeType,
                inspectedAtMs = 1L
            )
        )
    }
}
