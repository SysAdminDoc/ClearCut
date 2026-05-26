package com.novacut.editor.engine

import com.novacut.editor.engine.MixedRenderComposer.Benefit
import com.novacut.editor.engine.MixedRenderComposer.Engine
import com.novacut.editor.engine.MixedRenderComposer.IssueSeverity
import com.novacut.editor.engine.SmartRenderEngine.RenderRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the B.5 composer-plan decision table so the future
 * `VideoEngine.exportMixed(...)` orchestrator can rely on a stable shape.
 *
 * The four cases that matter:
 *  1. Empty input → NoBenefit, warning issued, no runs, no concat.
 *  2. Single run → SingleRun, run output IS the final output, no concat.
 *  3. All re-encode → NoBenefit (mixed plan adds no value), no concat,
 *     INFO issue tells caller to use the simple Transformer path.
 *  4. Mixed copy + re-encode → Mixed, run outputs named consistently,
 *     concat step lists every run in order.
 *
 * Plus: short stream-copy runs get a keyframe-alignment warning so the
 * orchestrator can either widen the boundary or fall back to re-encode.
 */
class MixedRenderComposerTest {

    @Test
    fun emptyRuns_returnsNoBenefitWithWarning() {
        val plan = MixedRenderComposer.plan(
            runs = emptyList(),
            projectStem = "p",
            finalOutputName = "out.mp4",
        )
        assertEquals(Benefit.NoBenefit, plan.benefit)
        assertTrue(plan.runs.isEmpty())
        assertNull(plan.concat)
        assertTrue(plan.issues.any { it.severity == IssueSeverity.WARNING })
    }

    @Test
    fun singleRun_returnsSingleRunPlanWithNoConcat() {
        val run = RenderRun(
            startMs = 0L, endMs = 5_000L,
            needsReEncode = false, clipIds = listOf("a"),
        )
        val plan = MixedRenderComposer.plan(
            runs = listOf(run),
            projectStem = "vlog",
            finalOutputName = "vlog-final.mp4",
        )
        assertEquals(Benefit.SingleRun, plan.benefit)
        assertEquals(1, plan.runs.size)
        // The single run's outputFileName is rewritten to the final output —
        // the orchestrator skips concat and writes straight there.
        assertEquals("vlog-final.mp4", plan.runs.single().outputFileName)
        assertNull(plan.concat)
        assertFalse(plan.needsConcat)
    }

    @Test
    fun allReEncodeRuns_returnsNoBenefit() {
        val runs = listOf(
            RenderRun(0L, 2_000L, needsReEncode = true, clipIds = listOf("a")),
            RenderRun(2_000L, 4_000L, needsReEncode = true, clipIds = listOf("b")),
            RenderRun(4_000L, 6_000L, needsReEncode = true, clipIds = listOf("c")),
        )
        val plan = MixedRenderComposer.plan(runs, "p", "out.mp4")
        assertEquals(Benefit.NoBenefit, plan.benefit)
        assertEquals(3, plan.runs.size)
        assertNull(plan.concat)
        assertTrue(plan.issues.any { it.severity == IssueSeverity.INFO })
    }

    @Test
    fun mixedRuns_emitsMixedPlanWithConcat() {
        val runs = listOf(
            RenderRun(0L, 2_000L, needsReEncode = false, clipIds = listOf("a")),
            RenderRun(2_000L, 4_000L, needsReEncode = true, clipIds = listOf("b")),
            RenderRun(4_000L, 6_000L, needsReEncode = false, clipIds = listOf("c")),
        )
        val plan = MixedRenderComposer.plan(runs, "vlog", "vlog-final.mp4")
        assertEquals(Benefit.Mixed, plan.benefit)
        assertEquals(3, plan.runs.size)
        assertEquals(Engine.STREAM_COPY, plan.runs[0].engine)
        assertEquals(Engine.TRANSFORMER, plan.runs[1].engine)
        assertEquals(Engine.STREAM_COPY, plan.runs[2].engine)
        assertNotNull(plan.concat)
        // Concat input list is the per-run output names in order.
        assertEquals(plan.runs.map { it.outputFileName }, plan.concat!!.inputs)
        assertEquals("vlog-final.mp4", plan.concat!!.outputFileName)
        assertTrue(plan.needsConcat)
    }

    @Test
    fun runOutputNames_encodeIndexAndEngineTag() {
        val runs = listOf(
            RenderRun(0L, 2_000L, needsReEncode = false, clipIds = listOf("a")),
            RenderRun(2_000L, 4_000L, needsReEncode = true, clipIds = listOf("b")),
        )
        val plan = MixedRenderComposer.plan(runs, "demo", "demo.mp4")
        assertEquals("demo-run00-cp.mp4", plan.runs[0].outputFileName)
        assertEquals("demo-run01-re.mp4", plan.runs[1].outputFileName)
    }

    @Test
    fun shortStreamCopyRun_emitsKeyframeAlignmentWarning() {
        val runs = listOf(
            RenderRun(0L, 100L, needsReEncode = false, clipIds = listOf("a")),
            RenderRun(100L, 5_100L, needsReEncode = true, clipIds = listOf("b")),
        )
        val plan = MixedRenderComposer.plan(runs, "p", "out.mp4")
        val keyframeWarnings = plan.issues.filter {
            it.severity == IssueSeverity.WARNING && it.runIndex == 0
        }
        assertEquals(1, keyframeWarnings.size)
        assertTrue(keyframeWarnings.single().message.contains("keyframe", ignoreCase = true))
    }

    @Test
    fun shortReEncodeRun_doesNotEmitKeyframeWarning() {
        // The warning is specific to stream-copy runs — re-encode reseeds
        // its own keyframes so short ranges are fine.
        val runs = listOf(
            RenderRun(0L, 100L, needsReEncode = true, clipIds = listOf("a")),
            RenderRun(100L, 5_100L, needsReEncode = false, clipIds = listOf("b")),
        )
        val plan = MixedRenderComposer.plan(runs, "p", "out.mp4")
        val keyframeWarnings = plan.issues.filter {
            it.severity == IssueSeverity.WARNING && it.runIndex == 0
        }
        assertTrue(keyframeWarnings.isEmpty())
    }

    @Test
    fun distinctEnginesUsed_reportsCorrectSet() {
        val mixed = MixedRenderComposer.plan(
            listOf(
                RenderRun(0L, 1_000L, needsReEncode = false, clipIds = listOf("a")),
                RenderRun(1_000L, 2_000L, needsReEncode = true, clipIds = listOf("b")),
            ),
            "p", "out.mp4",
        )
        assertEquals(setOf(Engine.STREAM_COPY, Engine.TRANSFORMER), mixed.distinctEnginesUsed)
    }

    @Test
    fun sanitiseStem_handlesEmptyAndExoticInput() {
        assertEquals("novacut", MixedRenderComposer.sanitiseStem(""))
        assertEquals("novacut", MixedRenderComposer.sanitiseStem("   "))
        // Path separators / colons / slashes collapse to underscores; runs
        // of underscores collapse cosmetically.
        assertEquals(
            "my_project_2026",
            MixedRenderComposer.sanitiseStem("my/project:2026")
        )
        // Stem is capped to 48 chars — important for FAT32 SAF destinations.
        val long = "a".repeat(120)
        assertTrue(MixedRenderComposer.sanitiseStem(long).length <= 48)
    }

    @Test
    fun finalExtension_isHonoredOnRunOutputNames() {
        val runs = listOf(
            RenderRun(0L, 1_000L, needsReEncode = false, clipIds = listOf("a")),
            RenderRun(1_000L, 2_000L, needsReEncode = true, clipIds = listOf("b")),
        )
        val mkv = MixedRenderComposer.plan(
            runs, "p", "out.mkv", finalExtension = ".mkv",
        )
        assertTrue(mkv.runs.all { it.outputFileName.endsWith(".mkv") })
        val noDot = MixedRenderComposer.plan(
            runs, "p", "out.webm", finalExtension = "webm",
        )
        assertTrue(noDot.runs.all { it.outputFileName.endsWith(".webm") })
    }
}
