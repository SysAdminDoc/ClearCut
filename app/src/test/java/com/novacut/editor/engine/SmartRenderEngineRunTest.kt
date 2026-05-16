package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B.5 — planRuns groups per-clip segments into contiguous runs that share an
 * encoding decision. Each run is then exported by the right engine
 * (StreamCopy for pass-through, Transformer / FFmpeg for re-encode) and the
 * outputs concatenated by a future composer step.
 */
class SmartRenderEngineRunTest {

    private fun seg(id: String, start: Long, end: Long, reEncode: Boolean) =
        SmartRenderEngine.RenderSegment(
            clipId = id,
            startMs = start,
            endMs = end,
            needsReEncode = reEncode,
            reason = if (reEncode) "test re-encode" else "pass-through"
        )

    @Test
    fun planRuns_emptyInput_returnsEmpty() {
        assertEquals(emptyList<SmartRenderEngine.RenderRun>(), SmartRenderEngine.planRuns(emptyList()))
    }

    @Test
    fun planRuns_singleSegment_oneRun() {
        val runs = SmartRenderEngine.planRuns(listOf(seg("a", 0, 1000, false)))
        assertEquals(1, runs.size)
        assertEquals(
            SmartRenderEngine.RenderRun(0, 1000, false, listOf("a")),
            runs.first()
        )
        assertEquals(1000L, runs.first().durationMs)
    }

    @Test
    fun planRuns_mergesContiguousSameFlag() {
        // Three consecutive pass-through clips collapse into a single run.
        val runs = SmartRenderEngine.planRuns(
            listOf(
                seg("a", 0, 1_000, false),
                seg("b", 1_000, 3_000, false),
                seg("c", 3_000, 4_500, false),
            )
        )
        assertEquals(1, runs.size)
        assertEquals(
            SmartRenderEngine.RenderRun(
                startMs = 0,
                endMs = 4_500,
                needsReEncode = false,
                clipIds = listOf("a", "b", "c")
            ),
            runs.first()
        )
    }

    @Test
    fun planRuns_breaksOnFlagChange() {
        // pass-through → re-encode → pass-through → 3 runs.
        val runs = SmartRenderEngine.planRuns(
            listOf(
                seg("a", 0, 1_000, false),
                seg("b", 1_000, 2_000, true),
                seg("c", 2_000, 3_000, false),
            )
        )
        assertEquals(3, runs.size)
        assertEquals(listOf("a"), runs[0].clipIds)
        assertEquals(listOf("b"), runs[1].clipIds)
        assertEquals(listOf("c"), runs[2].clipIds)
        assertEquals(false, runs[0].needsReEncode)
        assertEquals(true, runs[1].needsReEncode)
        assertEquals(false, runs[2].needsReEncode)
    }

    @Test
    fun planRuns_breaksOnTimelineGap_evenWhenFlagsMatch() {
        // Two pass-through clips with a 500 ms gap must stay in separate runs.
        // The gap-bridging step (black frame fill) is the composer's job and
        // only the re-encode path can produce it today.
        val runs = SmartRenderEngine.planRuns(
            listOf(
                seg("a", 0, 1_000, false),
                seg("b", 1_500, 2_500, false),
            )
        )
        assertEquals(2, runs.size)
        assertEquals(1_000L, runs[0].endMs)
        assertEquals(1_500L, runs[1].startMs)
    }

    @Test
    fun planRuns_handlesOutOfOrderInput() {
        // analyzeTimeline already sorts, but planRuns must not assume order.
        val runs = SmartRenderEngine.planRuns(
            listOf(
                seg("c", 2_000, 3_000, false),
                seg("a", 0, 1_000, false),
                seg("b", 1_000, 2_000, false),
            )
        )
        assertEquals(1, runs.size)
        assertEquals(listOf("a", "b", "c"), runs.first().clipIds)
        assertEquals(3_000L, runs.first().endMs)
    }

    @Test
    fun planRuns_durationSumsMatchInputs() {
        val segments = listOf(
            seg("a", 0, 1_000, false),
            seg("b", 1_000, 2_000, true),
            seg("c", 2_000, 5_000, true),
            seg("d", 5_000, 5_500, false),
        )
        val runs = SmartRenderEngine.planRuns(segments)
        val runTotal = runs.sumOf { it.durationMs }
        val inputTotal = segments.sumOf { it.endMs - it.startMs }
        assertEquals(inputTotal, runTotal)
    }

    @Test
    fun planRuns_alternatingFlags_eachRunHasOneClip() {
        val runs = SmartRenderEngine.planRuns(
            listOf(
                seg("a", 0, 1_000, false),
                seg("b", 1_000, 2_000, true),
                seg("c", 2_000, 3_000, false),
                seg("d", 3_000, 4_000, true),
            )
        )
        assertEquals(4, runs.size)
        runs.forEach { run ->
            assertEquals(1, run.clipIds.size)
        }
        // First and third runs are pass-through; second and fourth are re-encode.
        assertTrue(!runs[0].needsReEncode)
        assertTrue(runs[1].needsReEncode)
        assertTrue(!runs[2].needsReEncode)
        assertTrue(runs[3].needsReEncode)
    }
}
