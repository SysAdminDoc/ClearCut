package com.novacut.editor.engine

import com.novacut.editor.engine.ProjectColorPolicy.Coherence
import com.novacut.editor.engine.ProjectColorPolicy.DisplayTransform
import com.novacut.editor.engine.ProjectColorPolicy.WorkingColorSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the [ProjectColorPolicy] coherence + delivery predicates that the
 * future Settings color-policy panel and the existing
 * [ExportColorConfidenceEngine] consume.
 *
 * The data class itself is trivial; the value of these tests is preventing
 * a future enum addition (e.g. a new ACES variant or a third tone-map
 * algorithm) from silently breaking the coherence decision table.
 */
class ProjectColorPolicyTest {

    @Test
    fun defaultIsConservativeSdr() {
        val p = ProjectColorPolicy.DEFAULT
        assertEquals(WorkingColorSpace.SDR_BT709, p.workingColorSpace)
        assertEquals(DisplayTransform.NONE, p.displayTransform)
        assertEquals(Coherence.COHERENT, p.coherence())
        assertFalse(p.deliversHdr)
    }

    @Test
    fun sdrPlusNoneIsCoherent() {
        val p = ProjectColorPolicy(
            workingColorSpace = WorkingColorSpace.SDR_BT709,
            displayTransform = DisplayTransform.NONE,
        )
        assertEquals(Coherence.COHERENT, p.coherence())
    }

    @Test
    fun sdrPlusTonemapIsSdrTonemapNoopWarning() {
        // Tone-mapping SDR to SDR is a no-op the user likely didn't intend.
        val p = ProjectColorPolicy(
            workingColorSpace = WorkingColorSpace.SDR_BT709,
            displayTransform = DisplayTransform.BT2390_TONEMAP,
        )
        assertEquals(Coherence.SDR_TONEMAP_NOOP, p.coherence())
        assertFalse(p.deliversHdr)
    }

    @Test
    fun hdrPlusNoneIsHdrPassthroughDelivery() {
        for (space in listOf(
            WorkingColorSpace.HDR10_BT2020_PQ,
            WorkingColorSpace.HDR_HLG,
            WorkingColorSpace.ACES_AP1,
        )) {
            val p = ProjectColorPolicy(space, DisplayTransform.NONE)
            assertEquals(Coherence.HDR_PASSTHROUGH, p.coherence())
            assertTrue(
                "HDR working + NONE transform must mark the project as HDR delivery (space=$space)",
                p.deliversHdr,
            )
        }
    }

    @Test
    fun hdrPlusTonemapIsHdrToSdrAndDoesNotDeliverHdr() {
        for (transform in listOf(
            DisplayTransform.BT2390_TONEMAP,
            DisplayTransform.HABLE_TONEMAP,
        )) {
            val p = ProjectColorPolicy(WorkingColorSpace.HDR10_BT2020_PQ, transform)
            assertEquals(Coherence.HDR_TO_SDR_TONEMAP, p.coherence())
            assertFalse(
                "HDR working + tone-map must not claim HDR delivery (transform=$transform)",
                p.deliversHdr,
            )
        }
    }

    @Test
    fun everyWorkingColorSpaceHasASelfConsistentHdrFlag() {
        // SDR_BT709 is the only non-HDR working space today; every other
        // option must report isHdr = true. Adding a future SDR variant
        // without flipping this assertion would silently break the
        // coherence table above.
        for (space in WorkingColorSpace.entries) {
            val expectedHdr = space != WorkingColorSpace.SDR_BT709
            assertEquals(
                "WorkingColorSpace $space isHdr classification drifted",
                expectedHdr,
                space.isHdr
            )
        }
    }

    @Test
    fun displayNamesAreNonBlank() {
        for (s in WorkingColorSpace.entries) assertTrue(s.displayName.isNotBlank())
        for (t in DisplayTransform.entries) assertTrue(t.displayName.isNotBlank())
    }
}
