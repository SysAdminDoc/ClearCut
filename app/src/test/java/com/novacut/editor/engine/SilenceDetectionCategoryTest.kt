package com.novacut.editor.engine

import com.novacut.editor.engine.SilenceDetectionEngine.CutProposal
import com.novacut.editor.engine.SilenceDetectionEngine.ProposalCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the chip-row helpers `categorize` / `filterByCategory` /
 * `groupByCategory` that the unified Cut Assistant Review panel depends on
 * (RESEARCH_FEATURE_PLAN_2026-05-25 Highest-Value #6 / Batch 11).
 *
 * The interesting decisions:
 *  - A FILLER_WORD proposal with a multi-token matchedText is MULTI_WORD,
 *    even though both single and multi share the FILLER_WORD reason.
 *  - A FILLER_WORD proposal with no matchedText degrades to OTHER, not to
 *    SINGLE_WORD — that case should never come from the shipping detectors,
 *    but treating it as OTHER prevents an empty chip from claiming false
 *    membership.
 *  - `filterByCategory(_, emptySet())` is the chip-all-off case and returns
 *    the empty list (not the input list).
 */
class SilenceDetectionCategoryTest {

    private val engine = SilenceDetectionEngine()

    @Test
    fun categorize_silenceIsAlwaysSilenceBucket() {
        val p = CutProposal(0L, 500L, CutProposal.Reason.SILENCE)
        assertEquals(ProposalCategory.SILENCE, engine.categorize(p))
    }

    @Test
    fun categorize_singleWordFillerIsSingleBucket() {
        val p = CutProposal(0L, 500L, CutProposal.Reason.FILLER_WORD, matchedText = "um")
        assertEquals(ProposalCategory.SINGLE_WORD_FILLER, engine.categorize(p))
    }

    @Test
    fun categorize_multiWordFillerIsMultiBucket() {
        val p = CutProposal(0L, 500L, CutProposal.Reason.FILLER_WORD, matchedText = "you know")
        assertEquals(ProposalCategory.MULTI_WORD_FILLER, engine.categorize(p))
    }

    @Test
    fun categorize_fillerWithNoTextDegradesToOther() {
        val p = CutProposal(0L, 500L, CutProposal.Reason.FILLER_WORD, matchedText = null)
        assertEquals(ProposalCategory.OTHER, engine.categorize(p))
        val blank = CutProposal(0L, 500L, CutProposal.Reason.FILLER_WORD, matchedText = "   ")
        assertEquals(ProposalCategory.OTHER, engine.categorize(blank))
    }

    @Test
    fun filterByCategory_allChipsOffReturnsEmpty() {
        val proposals = listOf(
            CutProposal(0L, 1L, CutProposal.Reason.SILENCE),
            CutProposal(2L, 3L, CutProposal.Reason.FILLER_WORD, matchedText = "um"),
        )
        val filtered = engine.filterByCategory(proposals, emptySet())
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun filterByCategory_allChipsOnReturnsSameInstance() {
        val proposals = listOf(
            CutProposal(0L, 1L, CutProposal.Reason.SILENCE),
        )
        val filtered = engine.filterByCategory(
            proposals,
            ProposalCategory.entries.toSet(),
        )
        // Same reference — identity filter avoids re-allocating the list.
        assertSame(proposals, filtered)
    }

    @Test
    fun filterByCategory_keepsInputOrder() {
        val a = CutProposal(0L, 1L, CutProposal.Reason.SILENCE)
        val b = CutProposal(2L, 3L, CutProposal.Reason.FILLER_WORD, matchedText = "um")
        val c = CutProposal(4L, 5L, CutProposal.Reason.FILLER_WORD, matchedText = "you know")
        val d = CutProposal(6L, 7L, CutProposal.Reason.SILENCE)
        val filtered = engine.filterByCategory(
            listOf(a, b, c, d),
            setOf(ProposalCategory.SILENCE),
        )
        assertEquals(listOf(a, d), filtered)
    }

    @Test
    fun groupByCategory_bucketsByCategoryAndPreservesInputOrder() {
        val proposals = listOf(
            CutProposal(0L, 1L, CutProposal.Reason.SILENCE),
            CutProposal(2L, 3L, CutProposal.Reason.FILLER_WORD, matchedText = "um"),
            CutProposal(4L, 5L, CutProposal.Reason.FILLER_WORD, matchedText = "you know"),
            CutProposal(6L, 7L, CutProposal.Reason.SILENCE),
            CutProposal(8L, 9L, CutProposal.Reason.FILLER_WORD, matchedText = "uh"),
        )
        val grouped = engine.groupByCategory(proposals)
        assertEquals(2, grouped[ProposalCategory.SILENCE]!!.size)
        assertEquals(2, grouped[ProposalCategory.SINGLE_WORD_FILLER]!!.size)
        assertEquals(1, grouped[ProposalCategory.MULTI_WORD_FILLER]!!.size)
        // Insertion order preserved within bucket.
        assertEquals(0L, grouped[ProposalCategory.SILENCE]!!.first().startMs)
        assertEquals(6L, grouped[ProposalCategory.SILENCE]!!.last().startMs)
    }
}
