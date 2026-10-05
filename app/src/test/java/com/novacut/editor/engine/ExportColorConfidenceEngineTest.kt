package com.novacut.editor.engine

import android.net.FakeUri
import android.net.SecondFakeUri
import android.net.Uri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.ProjectColorPolicy
import com.novacut.editor.model.Resolution
import com.novacut.editor.model.SourceColorMetadata
import com.novacut.editor.model.SourceHdrFormat
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
            config = ExportConfig(codec = VideoCodec.H264),
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
            config = ExportConfig(codec = VideoCodec.HEVC),
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
        assertTrue(report.chips.any { it.detail.contains("Keep HDR") })
    }

    @Test
    fun hdrBaseGainMapSourceStillReportsUltraHdrSource() {
        val report = ExportColorConfidenceEngine.analyze(
            config = ExportConfig(codec = VideoCodec.HEVC),
            width = 1920,
            height = 1080,
            hdrSupport = ExportColorConfidenceEngine.HdrEncodeSupport(),
            sourceSummary = ExportColorConfidenceEngine.SourceHdrSummary(
                supportedFormats = setOf(SourceHdrFormat.ULTRA_HDR_HDR_BASE_GAIN_MAP.displayName),
                inspectedSourceCount = 1,
                totalSourceCount = 1
            )
        )

        assertFalse(report.hasWarnings)
        assertTrue(report.chips.any { chip ->
            chip.label == "Ultra HDR source" &&
                chip.detail.contains(SourceHdrFormat.ULTRA_HDR_HDR_BASE_GAIN_MAP.displayName)
        })
    }

    @Test
    fun sdrExportReportsApvSourceChipWithoutWarningList() {
        val report = ExportColorConfidenceEngine.analyze(
            config = ExportConfig(codec = VideoCodec.HEVC),
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
            config = ExportConfig(codec = VideoCodec.H264, colorPolicy = ProjectColorPolicy.KEEP_HDR),
            width = 1920,
            height = 1080,
            hdrSupport = ExportColorConfidenceEngine.HdrEncodeSupport(setOf("HDR10+"))
        )

        assertTrue(report.hasWarnings)
        assertTrue(report.warnings.first().contains("H.264 cannot carry HDR"))
        assertEquals(ExportColorConfidenceEngine.Tone.WARNING, report.chips.first().tone)
    }

    @Test
    fun projectColorChipNamesTheProjectDecision() {
        fun projectChip(policy: ProjectColorPolicy) = ExportColorConfidenceEngine.analyze(
            config = ExportConfig(codec = VideoCodec.HEVC, colorPolicy = policy),
            width = 1920,
            height = 1080,
            hdrSupport = ExportColorConfidenceEngine.HdrEncodeSupport(setOf("HDR10+")),
        ).chips.single { it.label == "Project color" }

        assertEquals(ExportColorConfidenceEngine.Tone.INFO, projectChip(ProjectColorPolicy.DEFAULT).tone)
        assertEquals(ExportColorConfidenceEngine.Tone.GOOD, projectChip(ProjectColorPolicy.KEEP_HDR).tone)
        val interpret = projectChip(ProjectColorPolicy(input = ProjectColorPolicy.InputColor.INTERPRET_HDR_AS_SDR))
        assertEquals(ExportColorConfidenceEngine.Tone.WARNING, interpret.tone)
        assertTrue(interpret.detail.contains("washed out"))
    }

    @Test
    fun hevcHdr10PlusSupportReportsDynamicMetadata() {
        val report = ExportColorConfidenceEngine.analyze(
            config = ExportConfig(codec = VideoCodec.HEVC, colorPolicy = ProjectColorPolicy.KEEP_HDR),
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
    fun hdrExportWithBitmapOverlaysDisclosesTheBlock() {
        val report = ExportColorConfidenceEngine.analyze(
            config = ExportConfig(codec = VideoCodec.HEVC, colorPolicy = ProjectColorPolicy.KEEP_HDR),
            width = 1920,
            height = 1080,
            hdrSupport = ExportColorConfidenceEngine.HdrEncodeSupport(setOf("HDR10+")),
            overlaySummary = HdrOverlaySummary(
                textOverlayCount = 1,
                imageOverlayCount = 1,
                watermarkPresent = true,
            ),
        )

        assertTrue(report.hasWarnings)
        assertTrue(report.warnings.any { it.contains("HDR can't be kept") })
        assertTrue(report.chips.any { it.label == "HDR overlays" })
    }

    @Test
    fun av1DolbyVisionProfile10SupportReportsDynamicPath() {
        val report = ExportColorConfidenceEngine.analyze(
            config = ExportConfig(codec = VideoCodec.AV1, colorPolicy = ProjectColorPolicy.KEEP_HDR),
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
            config = ExportConfig(codec = VideoCodec.HEVC, colorPolicy = ProjectColorPolicy.KEEP_HDR),
            width = 1920,
            height = 1080,
            hdrSupport = ExportColorConfidenceEngine.HdrEncodeSupport()
        )

        assertTrue(report.hasWarnings)
        assertTrue(report.warnings.any { it.contains("does not advertise HDR encode support") })
    }

    @Test
    fun profileNamesDoNotHideMissingHdrEditingFeature() {
        val report = ExportColorConfidenceEngine.analyze(
            config = ExportConfig(codec = VideoCodec.HEVC, colorPolicy = ProjectColorPolicy.KEEP_HDR),
            width = 1920,
            height = 1080,
            hdrSupport = ExportColorConfidenceEngine.HdrEncodeSupport(
                supportedFormats = setOf("HDR10"),
                featureSupport = EncoderCapabilityProbe.HdrFeatureSupport(),
            ),
        )

        assertTrue(report.hasWarnings)
        assertTrue(report.chips.any { it.label == "HDR unavailable" })
        assertTrue(report.warnings.any { it.contains("FEATURE_HlgEditing") })
    }

    @Test
    fun advertisedHdrEditingFeatureCanOpenGateWithoutProfileName() {
        val report = ExportColorConfidenceEngine.analyze(
            config = ExportConfig(codec = VideoCodec.HEVC, colorPolicy = ProjectColorPolicy.KEEP_HDR),
            width = 1920,
            height = 1080,
            hdrSupport = ExportColorConfidenceEngine.HdrEncodeSupport(
                featureSupport = EncoderCapabilityProbe.HdrFeatureSupport(hdrEditing = true),
            ),
        )

        assertFalse(report.hasWarnings)
        assertTrue(report.chips.any { it.label == "HDR feature gate" })
    }

    @Test
    fun hdrRequestWarnsWhenExportExceedsAdvertisedHdrLimits() {
        val report = ExportColorConfidenceEngine.analyze(
            config = ExportConfig(
                resolution = Resolution.UHD_4K,
                codec = VideoCodec.HEVC,
                colorPolicy = ProjectColorPolicy.KEEP_HDR
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
