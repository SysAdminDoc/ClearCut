package com.novacut.editor.engine

import android.net.FakeUri
import com.novacut.editor.model.Caption
import com.novacut.editor.model.Clip
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.ProjectColorPolicy
import com.novacut.editor.model.SourceColorMetadata
import com.novacut.editor.model.SourceHdrFormat
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import com.novacut.editor.model.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorRenderPlannerTest {

    private val keep = ProjectColorPolicy.KEEP_HDR
    private val interpret = ProjectColorPolicy(input = ProjectColorPolicy.InputColor.INTERPRET_HDR_AS_SDR)

    @Test
    fun sdrProjectToneMapsHdrClipsInPreviewAndExport() {
        val plan = plan(ProjectColorPolicy.DEFAULT, track(clip("a", 0L, transfer = "HLG")))

        assertEquals(ColorHdrMode.TONE_MAP_TO_SDR, plan.hdrMode)
        assertEquals(ColorHdrMode.TONE_MAP_TO_SDR, plan.previewHdrMode)
        assertEquals(DeliveredColor.SDR, plan.expected)
        assertTrue(plan.issues.isEmpty())
    }

    @Test
    fun keepHdrDeliversTheTransferOfTheFootage() {
        val hlg = plan(keep, track(clip("a", 0L, transfer = "HLG"), clip("b", 1_000L, transfer = "HLG")))
        val pq = plan(keep, track(clip("a", 0L, transfer = "ST 2084")))
        val hdr10Tagged = plan(keep, track(clip("a", 0L, transfer = null, formats = setOf(SourceHdrFormat.HDR10))))

        assertEquals(ColorHdrMode.KEEP_HDR, hlg.hdrMode)
        assertEquals(ColorHdrMode.KEEP_HDR, hlg.previewHdrMode)
        assertEquals(DeliveredColor.HLG, hlg.expected)
        assertTrue(hlg.issues.isEmpty())
        assertEquals(DeliveredColor.PQ, pq.expected)
        assertEquals(DeliveredColor.PQ, hdr10Tagged.expected)
    }

    @Test
    fun keepHdrWithoutHdrFootageWarnsAndDeliversSdr() {
        val plan = plan(keep, track(clip("a", 0L, transfer = null)))

        assertEquals(DeliveredColor.SDR, plan.expected)
        assertEquals(setOf(ColorPlanIssue.NO_HDR_CLIPS), plan.issues)
        assertFalse(plan.isBlocked)
    }

    @Test
    fun mixedHlgAndPqWarnsAndKeepsTheFirstTransfer() {
        val plan = plan(keep, track(clip("a", 0L, transfer = "HLG"), clip("b", 1_000L, transfer = "ST 2084")))

        assertEquals(DeliveredColor.HLG, plan.expected)
        assertEquals(setOf(ColorPlanIssue.MIXED_HDR_TRANSFERS), plan.issues)
        assertFalse(plan.isBlocked)
    }

    @Test
    fun sdrClipsGapsAndStillsBlockKeepHdrAndPreviewFallsBackToToneMapping() {
        val sdrClip = plan(keep, track(clip("a", 0L, transfer = "HLG"), clip("b", 1_000L, transfer = null)))
        val gap = plan(keep, track(clip("a", 0L, transfer = "HLG"), clip("b", 2_000L, transfer = "HLG")))
        val still = plan(
            keep,
            track(clip("a", 0L, transfer = "HLG"), clip("b", 1_000L, transfer = null, mimeType = "image/jpeg")),
        )

        for (blocked in listOf(sdrClip, gap, still)) {
            assertEquals(listOf(ColorPlanIssue.SDR_MIXED_WITH_HDR), blocked.blockers)
            assertTrue(blocked.isBlocked)
            assertEquals(ColorHdrMode.KEEP_HDR, blocked.hdrMode)
            assertEquals(ColorHdrMode.TONE_MAP_TO_SDR, blocked.previewHdrMode)
        }
    }

    @Test
    fun stackedHdrTracksH264AndSdrOverlaysBlock() {
        val stacked = plan(
            keep,
            track(clip("a", 0L, transfer = "HLG")),
            track(clip("b", 0L, transfer = "HLG"), index = 1, type = TrackType.OVERLAY),
        )
        val h264 = plan(keep, track(clip("a", 0L, transfer = "HLG")), codec = VideoCodec.H264)
        val overlays = plan(
            keep,
            track(clip("a", 0L, transfer = "HLG")),
            overlays = HdrOverlaySummary(textOverlayCount = 1, imageOverlayCount = 1, watermarkPresent = true),
        )

        assertTrue(ColorPlanIssue.STACKED_HDR in stacked.blockers)
        assertEquals(listOf(ColorPlanIssue.CODEC_CANNOT_CARRY_HDR), h264.blockers)
        assertEquals(listOf(ColorPlanIssue.OVERLAYS_NEED_SDR), overlays.blockers)
    }

    @Test
    fun uncheckedClipsWarnInsteadOfCountingAsSdr() {
        val plan = plan(keep, track(clip("a", 0L, transfer = "HLG"), clip("b", 1_000L, transfer = null, inspected = false)))

        assertEquals(setOf(ColorPlanIssue.UNINSPECTED_CLIPS), plan.issues)
        assertEquals(DeliveredColor.HLG, plan.expected)
    }

    @Test
    fun interpretingHdrAsSdrWarnsAndNeverKeepsHdr() {
        val withHdr = plan(interpret, track(clip("a", 0L, transfer = "HLG")))
        val sdrOnly = plan(interpret, track(clip("a", 0L, transfer = null)))

        assertEquals(ColorHdrMode.INTERPRET_HDR_AS_SDR, withHdr.hdrMode)
        assertEquals(DeliveredColor.SDR, withHdr.expected)
        assertEquals(setOf(ColorPlanIssue.HDR_READ_AS_SDR), withHdr.issues)
        assertTrue(sdrOnly.issues.isEmpty())
    }

    @Test
    fun devicesWhoseGpuCantSampleHdrToneMapElsewhereAndCantKeepHdr() {
        val hlg = track(clip("a", 0L, transfer = "HLG"))
        val decoder = HdrDeviceSupport.DECODER_TONE_MAP_ONLY

        val sdrOnDecoder = plan(ProjectColorPolicy.DEFAULT, hlg, device = decoder)
        val sdrOnNone = plan(ProjectColorPolicy.DEFAULT, hlg, device = HdrDeviceSupport.NONE)
        val sdrClipsOnNone = plan(ProjectColorPolicy.DEFAULT, track(clip("a", 0L, transfer = null)), device = HdrDeviceSupport.NONE)
        val keepOnDecoder = plan(keep, hlg, device = decoder)
        val keepWithoutHdrOnDecoder = plan(keep, track(clip("a", 0L, transfer = null)), device = decoder)

        assertEquals(ColorHdrMode.TONE_MAP_IN_DECODER, sdrOnDecoder.hdrMode)
        assertTrue(sdrOnDecoder.issues.isEmpty())
        assertEquals(ColorHdrMode.INTERPRET_HDR_AS_SDR, sdrOnNone.hdrMode)
        assertEquals(setOf(ColorPlanIssue.HDR_READ_AS_SDR), sdrOnNone.issues)
        assertTrue(sdrClipsOnNone.issues.isEmpty())
        assertEquals(ColorHdrMode.TONE_MAP_TO_SDR, sdrClipsOnNone.hdrMode)
        assertEquals(listOf(ColorPlanIssue.DEVICE_CANNOT_PROCESS_HDR), keepOnDecoder.blockers)
        assertEquals(ColorHdrMode.TONE_MAP_IN_DECODER, keepOnDecoder.previewHdrMode)
        assertFalse(keepWithoutHdrOnDecoder.isBlocked)
    }

    @Test
    fun exportPlanUsesVp9ForTransparentOutput() {
        val tracks = listOf(track(clip("a", 0L, transfer = "HLG")))
        val opaque = ColorRenderPlanner.planExport(ExportConfig(codec = VideoCodec.H264, colorPolicy = keep), tracks)
        val transparent = ColorRenderPlanner.planExport(
            ExportConfig(codec = VideoCodec.H264, colorPolicy = keep, transparentBackground = true),
            tracks,
        )

        assertTrue(ColorPlanIssue.CODEC_CANNOT_CARRY_HDR in opaque.blockers)
        assertFalse(transparent.isBlocked)
        assertEquals(DeliveredColor.HLG, transparent.expected)
    }

    @Test
    fun burningCaptionsIntoAKeptHdrExportBlocksIt() {
        val hlg = clip("a", 0L, transfer = "HLG")
        val captioned = hlg.copy(captions = listOf(Caption(text = "Hi", startTimeMs = 0L, endTimeMs = 500L)))
        val burn = ExportConfig(codec = VideoCodec.HEVC, colorPolicy = keep, burnSubtitles = true)

        val blocked = ColorRenderPlanner.planExport(burn, listOf(track(captioned)))
        val noCaptions = ColorRenderPlanner.planExport(burn, listOf(track(hlg)))
        val sidecarOnly = ColorRenderPlanner.planExport(burn.copy(burnSubtitles = false), listOf(track(captioned)))
        val sdrPolicy = ColorRenderPlanner.planExport(
            burn.copy(colorPolicy = ProjectColorPolicy.DEFAULT),
            listOf(track(captioned)),
        )
        val sdrSource = ColorRenderPlanner.planExport(
            burn,
            listOf(track(clip("b", 0L, transfer = null).copy(captions = captioned.captions))),
        )

        assertEquals(listOf(ColorPlanIssue.SUBTITLE_BURN_IN_IS_SDR), blocked.blockers)
        assertFalse(noCaptions.isBlocked)
        assertFalse(sidecarOnly.isBlocked)
        assertFalse(sdrPolicy.isBlocked)
        assertFalse(sdrSource.isBlocked)
    }

    @Test
    fun copiedBitstreamsMustAlreadyMatchThePolicy() {
        val sdr = clip("a", 0L, transfer = null)
        val hlg = clip("b", 0L, transfer = "HLG")
        val unchecked = clip("c", 0L, transfer = null, inspected = false)

        assertTrue(ColorRenderPlanner.copyHonorsPolicy(ProjectColorPolicy.DEFAULT, sdr))
        assertTrue(ColorRenderPlanner.copyHonorsPolicy(keep, sdr))
        assertFalse(ColorRenderPlanner.copyHonorsPolicy(ProjectColorPolicy.DEFAULT, hlg))
        assertTrue(ColorRenderPlanner.copyHonorsPolicy(keep, hlg))
        assertFalse(ColorRenderPlanner.copyHonorsPolicy(keep, unchecked))
        assertFalse(ColorRenderPlanner.copyHonorsPolicy(interpret, sdr))
    }

    @Test
    fun everyIssueHasItsOwnMessage() {
        val messages = ColorPlanIssue.entries.map { it.messageRes }

        assertEquals(ColorPlanIssue.entries.size, messages.toSet().size)
        assertTrue(messages.all { it != 0 })
    }

    private fun plan(
        policy: ProjectColorPolicy,
        vararg tracks: Track,
        codec: VideoCodec = VideoCodec.HEVC,
        overlays: HdrOverlaySummary? = null,
        device: HdrDeviceSupport = HdrDeviceSupport.OPEN_GL,
    ): ColorRenderPlan = ColorRenderPlanner.plan(
        policy = policy,
        visualTracks = tracks.toList(),
        totalDurationMs = tracks.maxOf { track -> track.clips.maxOf { it.timelineStartMs + it.durationMs } },
        codec = codec,
        overlays = overlays,
        device = device,
    )

    private fun track(vararg clips: Clip, index: Int = 0, type: TrackType = TrackType.VIDEO) = Track(
        id = "track-$index",
        type = type,
        index = index,
        clips = clips.toList(),
    )

    private fun clip(
        id: String,
        startMs: Long,
        transfer: String?,
        formats: Set<SourceHdrFormat> = emptySet(),
        mimeType: String = "video/hevc",
        inspected: Boolean = true,
    ) = Clip(
        id = id,
        sourceUri = FakeUri,
        sourceDurationMs = 10_000L,
        timelineStartMs = startMs,
        trimStartMs = 0L,
        trimEndMs = 1_000L,
        sourceColorMetadata = SourceColorMetadata(
            mimeType = mimeType,
            colorTransfer = transfer,
            hdrFormats = formats,
            inspectedAtMs = if (inspected) 1L else 0L,
        ),
    )
}
