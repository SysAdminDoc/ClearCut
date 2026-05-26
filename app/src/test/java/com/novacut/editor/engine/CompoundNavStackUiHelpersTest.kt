package com.novacut.editor.engine

import android.net.FakeUri
import com.novacut.editor.model.Clip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the UI-facing helpers on [CompoundNavStack] that the future Timeline
 * radial-menu gesture + breadcrumb chip will consume (RESEARCH_FEATURE_PLAN
 * Highest-Value #5 / ROADMAP C.13 UI). Pre-existing `CompoundNavStackTest`
 * covers the push / pop / cycle-rejection contract; this file adds coverage
 * for the new `canPush` predicate and the `formatBreadcrumb` formatter.
 */
class CompoundNavStackUiHelpersTest {

    @Test
    fun canPush_returnsTrueForCompoundClip() {
        val s = CompoundNavStack()
        assertTrue(s.canPush(compoundClip("c1")))
    }

    @Test
    fun canPush_returnsFalseForNonCompoundClip() {
        val s = CompoundNavStack()
        assertFalse(s.canPush(regularClip("r1")))
    }

    @Test
    fun canPush_returnsFalseForCycle() {
        val s = CompoundNavStack()
        val c = compoundClip("c1")
        s.push(c)
        // Trying to descend into the same compound clip again would loop.
        assertFalse(s.canPush(c))
    }

    @Test
    fun canPush_returnsFalseAtMaxDepth() {
        val s = CompoundNavStack()
        // Fill to MAX_DEPTH with unique compound clips.
        for (i in 1..CompoundNavStack.MAX_DEPTH) {
            s.push(compoundClip("c$i"))
        }
        // The next push would exceed the cap.
        val overflow = compoundClip("c-overflow")
        assertFalse(s.canPush(overflow))
    }

    @Test
    fun formatBreadcrumb_atRoot_returnsRootLabel() {
        val s = CompoundNavStack()
        assertEquals("Project", s.formatBreadcrumb())
        assertEquals("Edit", s.formatBreadcrumb(rootLabel = "Edit"))
    }

    @Test
    fun formatBreadcrumb_includesEveryParentClipName() {
        val s = CompoundNavStack()
        s.push(compoundClip("c1", name = "Intro Sequence"))
        s.push(compoundClip("c2", name = "Inner Title"))
        assertEquals(
            "Project ▸ Intro Sequence ▸ Inner Title",
            s.formatBreadcrumb()
        )
    }

    @Test
    fun formatBreadcrumb_usesFallbackForBlankParentName() {
        val s = CompoundNavStack()
        s.push(compoundClip("c1", name = ""))
        s.push(compoundClip("c2", name = "Named"))
        // Fallback at depth 1 (the index of the blank-name entry in breadcrumb).
        val rendered = s.formatBreadcrumb(
            fallbackParentName = { depth -> "Level $depth" },
        )
        assertEquals("Project ▸ Level 1 ▸ Named", rendered)
    }

    @Test
    fun formatBreadcrumb_respectsCustomSeparator() {
        val s = CompoundNavStack()
        s.push(compoundClip("c1", name = "A"))
        s.push(compoundClip("c2", name = "B"))
        assertEquals(
            "Project / A / B",
            s.formatBreadcrumb(separator = " / ")
        )
    }

    private fun compoundClip(id: String, name: String? = "Compound $id"): Clip = Clip(
        id = id,
        name = name,
        sourceUri = FakeUri,
        sourceDurationMs = 10_000L,
        trimStartMs = 0L,
        trimEndMs = 10_000L,
        timelineStartMs = 0L,
        isCompound = true,
    )

    private fun regularClip(id: String): Clip = Clip(
        id = id,
        name = "Clip $id",
        sourceUri = FakeUri,
        sourceDurationMs = 5_000L,
        trimStartMs = 0L,
        trimEndMs = 5_000L,
        timelineStartMs = 0L,
        isCompound = false,
    )
}
