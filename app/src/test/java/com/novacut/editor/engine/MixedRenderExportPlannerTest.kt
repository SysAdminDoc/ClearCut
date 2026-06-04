package com.novacut.editor.engine

import android.net.FakeUri
import android.net.Uri
import com.novacut.editor.model.Effect
import com.novacut.editor.model.EffectType
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MixedRenderExportPlannerTest {

    @Test
    fun buildPlan_cleanEffectCleanVideoTrack_returnsMixedPlan() {
        val tracks = listOf(
            videoTrack(
                baseClip(id = "a", startMs = 0L),
                baseClip(id = "b", startMs = 1_000L).copy(
                    effects = listOf(Effect(type = EffectType.BRIGHTNESS))
                ),
                baseClip(id = "c", startMs = 2_000L),
            )
        )

        val plan = MixedRenderExportPlanner.buildPlan(
            tracks = tracks,
            config = ExportConfig(),
            finalOutputName = "demo.mp4",
            projectStem = "demo",
        )

        assertNotNull(plan)
        requireNotNull(plan)
        assertEquals(MixedRenderComposer.Benefit.Mixed, plan.benefit)
        assertEquals(
            listOf(
                MixedRenderComposer.Engine.STREAM_COPY,
                MixedRenderComposer.Engine.TRANSFORMER,
                MixedRenderComposer.Engine.STREAM_COPY,
            ),
            plan.runs.map { it.engine }
        )
        assertEquals(listOf("demo-run00-cp.mp4", "demo-run01-re.mp4", "demo-run02-cp.mp4"), plan.concat?.inputs)
    }

    @Test
    fun buildPlan_audioTrackPresent_rejectsMixedPlan() {
        val tracks = listOf(
            videoTrack(
                baseClip(id = "a", startMs = 0L),
                baseClip(id = "b", startMs = 1_000L).copy(
                    effects = listOf(Effect(type = EffectType.CONTRAST))
                ),
            ),
            Track(
                type = TrackType.AUDIO,
                index = 1,
                clips = listOf(baseClip(id = "audio", startMs = 0L)),
            )
        )

        assertEquals(
            "separate audio track present",
            MixedRenderExportPlanner.rejectionReason(tracks, ExportConfig())
        )
        assertNull(
            MixedRenderExportPlanner.buildPlan(
                tracks = tracks,
                config = ExportConfig(),
                finalOutputName = "demo.mp4",
                projectStem = "demo",
            )
        )
    }

    @Test
    fun buildPlan_nonVideoExportShape_rejectsMixedPlan() {
        val tracks = listOf(
            videoTrack(
                baseClip(id = "a", startMs = 0L),
                baseClip(id = "b", startMs = 1_000L).copy(
                    effects = listOf(Effect(type = EffectType.SATURATION))
                ),
            )
        )

        assertEquals(
            "target-size bitrate requested",
            MixedRenderExportPlanner.rejectionReason(
                tracks = tracks,
                config = ExportConfig(targetSizeBytes = 1_000_000L)
            )
        )
    }

    @Test
    fun sliceTracksForRun_normalisesTransformerTimeline() {
        val first = baseClip(id = "a", startMs = 0L)
        val second = baseClip(id = "b", startMs = 1_000L).copy(
            effects = listOf(Effect(type = EffectType.BRIGHTNESS))
        )
        val third = baseClip(id = "c", startMs = 2_000L)
        val tracks = listOf(videoTrack(first, second, third))
        val run = SmartRenderEngine.RenderRun(
            startMs = 1_000L,
            endMs = 2_000L,
            needsReEncode = true,
            clipIds = listOf("b"),
        )

        val sliced = MixedRenderExportPlanner.sliceTracksForRun(
            tracks = tracks,
            run = run,
            normaliseTimelineStart = true,
        )

        assertEquals(1, sliced.size)
        assertEquals(listOf("b"), sliced.single().clips.map { it.id })
        assertEquals(0L, sliced.single().clips.single().timelineStartMs)
        assertEquals(second.trimStartMs, sliced.single().clips.single().trimStartMs)
    }

    private fun videoTrack(vararg clips: Clip): Track = Track(
        type = TrackType.VIDEO,
        index = 0,
        clips = clips.toList(),
    )

    private fun baseClip(
        id: String,
        startMs: Long,
        uri: Uri = FakeUri as Uri,
    ): Clip = Clip(
        id = id,
        sourceUri = uri,
        sourceDurationMs = 10_000L,
        timelineStartMs = startMs,
        trimStartMs = startMs,
        trimEndMs = startMs + 1_000L,
    )
}
