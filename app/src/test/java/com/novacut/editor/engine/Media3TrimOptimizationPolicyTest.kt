package com.novacut.editor.engine

import android.net.FakeUri
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Effect
import com.novacut.editor.model.EffectType
import com.novacut.editor.model.ExportConfig
import com.novacut.editor.model.TimelineExportRange
import com.novacut.editor.model.Track
import com.novacut.editor.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Media3TrimOptimizationPolicyTest {
    @Test
    fun acceptsSingleMp4WithHeadOrTailTrim() {
        val decision = Media3TrimOptimizationPolicy.evaluate(
            tracks = listOf(videoTrack(clip(trimStartMs = 1_000L, trimEndMs = 9_000L))),
            config = ExportConfig(),
            inputMimeType = "video/mp4",
        )

        assertTrue(decision.eligible)
        assertEquals(Media3TrimOptimizationPolicy.Reason.ELIGIBLE, decision.reason)
    }

    @Test
    fun enablesEditListOnlyWhenTheSourceCanBeTransmuxed() {
        val sourceFormat = Media3TrimOptimizationPolicy.InputFormat(
            videoMimeType = "video/avc",
            videoWidth = 854,
            videoHeight = 480,
            videoFrameRate = 30f,
            audioMimeType = "audio/mp4a-latm",
        )
        val compatible = Media3TrimOptimizationPolicy.evaluate(
            tracks = listOf(videoTrack(clip(trimStartMs = 1_000L, trimEndMs = 9_000L))),
            config = ExportConfig(),
            inputMimeType = "video/mp4",
            inputFormat = sourceFormat,
            outputWidth = 854,
            outputHeight = 480,
            outputFrameRate = 30,
        )
        val resizing = Media3TrimOptimizationPolicy.evaluate(
            tracks = listOf(videoTrack(clip(trimStartMs = 1_000L, trimEndMs = 9_000L))),
            config = ExportConfig(),
            inputMimeType = "video/mp4",
            inputFormat = sourceFormat,
            outputWidth = 1_920,
            outputHeight = 1_080,
            outputFrameRate = 30,
        )

        assertTrue(compatible.eligible)
        assertTrue(compatible.mp4EditListTrimEligible)
        assertTrue(resizing.eligible)
        assertFalse(resizing.mp4EditListTrimEligible)
    }

    @Test
    fun rejectsNonMp4AndUntrimmedInputs() {
        val nonMp4 = Media3TrimOptimizationPolicy.evaluate(
            tracks = listOf(videoTrack(clip(trimStartMs = 1_000L, trimEndMs = 9_000L))),
            config = ExportConfig(),
            inputMimeType = "video/quicktime",
        )
        val untrimmed = Media3TrimOptimizationPolicy.evaluate(
            tracks = listOf(videoTrack(clip())),
            config = ExportConfig(),
            inputMimeType = "video/mp4",
        )

        assertEquals(Media3TrimOptimizationPolicy.Reason.NOT_MP4_INPUT, nonMp4.reason)
        assertEquals(Media3TrimOptimizationPolicy.Reason.NO_TRIM, untrimmed.reason)
    }

    @Test
    fun rejectsEveryNonPureTimelineShape() {
        val secondClip = Media3TrimOptimizationPolicy.evaluate(
            tracks = listOf(
                videoTrack(clip(trimStartMs = 1_000L, trimEndMs = 9_000L)),
                Track(
                    type = TrackType.VIDEO,
                    index = 1,
                    clips = listOf(clip(id = "second", trimStartMs = 500L, trimEndMs = 2_000L)),
                ),
            ),
            config = ExportConfig(),
            inputMimeType = "video/mp4",
        )
        val speed = Media3TrimOptimizationPolicy.evaluate(
            tracks = listOf(videoTrack(clip(trimStartMs = 1_000L, trimEndMs = 9_000L, speed = 2f))),
            config = ExportConfig(),
            inputMimeType = "video/mp4",
        )
        val effect = Media3TrimOptimizationPolicy.evaluate(
            tracks = listOf(
                videoTrack(
                    clip(
                        trimStartMs = 1_000L,
                        trimEndMs = 9_000L,
                        effects = listOf(Effect(type = EffectType.GAUSSIAN_BLUR)),
                    )
                )
            ),
            config = ExportConfig(),
            inputMimeType = "video/mp4",
        )
        val rotation = Media3TrimOptimizationPolicy.evaluate(
            tracks = listOf(
                videoTrack(
                    clip(trimStartMs = 1_000L, trimEndMs = 9_000L, rotation = 45f)
                )
            ),
            config = ExportConfig(),
            inputMimeType = "video/mp4",
        )
        val flip = Media3TrimOptimizationPolicy.evaluate(
            tracks = listOf(
                videoTrack(
                    clip(trimStartMs = 1_000L, trimEndMs = 9_000L).copy(flipVertical = true)
                )
            ),
            config = ExportConfig(),
            inputMimeType = "video/mp4",
        )

        assertEquals(Media3TrimOptimizationPolicy.Reason.NOT_SINGLE_VIDEO_ASSET, secondClip.reason)
        assertEquals(Media3TrimOptimizationPolicy.Reason.SPEED_CHANGE, speed.reason)
        assertEquals(Media3TrimOptimizationPolicy.Reason.VIDEO_EDIT, effect.reason)
        assertEquals(Media3TrimOptimizationPolicy.Reason.UNSUPPORTED_ROTATION, rotation.reason)
        assertEquals(Media3TrimOptimizationPolicy.Reason.VIDEO_EDIT, flip.reason)
        assertFalse(secondClip.eligible || speed.eligible || effect.eligible || rotation.eligible || flip.eligible)
    }

    @Test
    fun rejectsOverlaysSpecialExportsAndResume() {
        val overlay = Media3TrimOptimizationPolicy.evaluate(
            tracks = listOf(videoTrack(clip(trimStartMs = 1_000L, trimEndMs = 9_000L))),
            config = ExportConfig(),
            inputMimeType = "video/mp4",
            textOverlayCount = 1,
        )
        val special = Media3TrimOptimizationPolicy.evaluate(
            tracks = listOf(videoTrack(clip(trimStartMs = 1_000L, trimEndMs = 9_000L))),
            config = ExportConfig(timelineRange = TimelineExportRange(0L, 1L)),
            inputMimeType = "video/mp4",
        )
        val constantFrameRate = Media3TrimOptimizationPolicy.evaluate(
            tracks = listOf(videoTrack(clip(trimStartMs = 1_000L, trimEndMs = 9_000L))),
            config = ExportConfig(forceConstantFrameRate = true),
            inputMimeType = "video/mp4",
        )
        val resume = Media3TrimOptimizationPolicy.evaluate(
            tracks = listOf(videoTrack(clip(trimStartMs = 1_000L, trimEndMs = 9_000L))),
            config = ExportConfig(),
            inputMimeType = "video/mp4",
            resumeRequested = true,
        )

        assertEquals(Media3TrimOptimizationPolicy.Reason.OVERLAYS_OR_AUTOMATION, overlay.reason)
        assertEquals(Media3TrimOptimizationPolicy.Reason.SPECIAL_EXPORT, special.reason)
        assertEquals(Media3TrimOptimizationPolicy.Reason.SPECIAL_EXPORT, constantFrameRate.reason)
        assertEquals(Media3TrimOptimizationPolicy.Reason.RESUME_REQUESTED, resume.reason)
    }

    @Test
    fun transformerFlagsAreGatedAndCompletionStillVerifiesOutput() {
        val source = locate("app/src/main/java/com/novacut/editor/engine/VideoEngine.kt").readText()
        assertTrue(source.contains("trimOptimizationEnabled = trimOptimizationDecision.eligible"))

        val flagBlock = source
            .substringAfter("if (trimOptimizationEnabled) {")
            .substringBefore("val transformer = transformerBuilder.build()")
        assertTrue(flagBlock.contains("experimentalSetTrimOptimizationEnabled(true)"))
        assertTrue(flagBlock.contains("experimentalSetMp4EditListTrimEnabled(true)"))

        val completion = source
            .substringAfter("override fun onCompleted(composition: Composition, exportResult: ExportResult)")
            .substringBefore("override fun onError(")
        assertTrue(completion.contains("ExportOutputVerifier.verify("))
    }

    private fun videoTrack(clip: Clip) = Track(
        type = TrackType.VIDEO,
        index = 0,
        clips = listOf(clip),
    )

    private fun clip(
        id: String = "video",
        trimStartMs: Long = 0L,
        trimEndMs: Long = 10_000L,
        speed: Float = 1f,
        rotation: Float = 0f,
        effects: List<Effect> = emptyList(),
    ) = Clip(
        id = id,
        sourceUri = FakeUri,
        sourceDurationMs = 10_000L,
        timelineStartMs = 0L,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        speed = speed,
        rotation = rotation,
        effects = effects,
    )

    private fun locate(relative: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, relative)
            if (candidate.isFile) return candidate
            current = current.parentFile ?: return@repeat
        }
        error("Could not locate $relative")
    }
}
