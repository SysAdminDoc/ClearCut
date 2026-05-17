package com.novacut.editor.engine

import android.net.FakeUri
import com.novacut.editor.model.Clip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C.13 — CompoundNavStack tests.
 */
class CompoundNavStackTest {

    private fun compoundClip(
        id: String,
        name: String = "Compound $id",
        children: List<Clip> = emptyList(),
    ): Clip = Clip(
        id = id,
        name = name,
        sourceUri = FakeUri,
        sourceDurationMs = 10_000L,
        trimStartMs = 0L,
        trimEndMs = 10_000L,
        timelineStartMs = 0L,
        isCompound = true,
        compoundClips = children,
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

    @Test
    fun fresh_stackStartsAtRoot() {
        val s = CompoundNavStack()
        assertTrue(s.isAtRoot)
        assertEquals(0, s.depth)
        assertTrue(s.currentLevel.isRoot)
        assertNull(s.currentLevel.parentClipId)
    }

    @Test
    fun push_descendsIntoCompoundClip() {
        val s = CompoundNavStack()
        val c = compoundClip("comp-1", "Intro Sequence")
        val level = s.push(c)
        assertEquals(1, s.depth)
        assertEquals("comp-1", level.parentClipId)
        assertEquals("Intro Sequence", level.parentClipName)
        assertEquals("comp-1", s.currentLevel.parentClipId)
        assertEquals(2, s.breadcrumb.size)
    }

    @Test
    fun push_rejectsNonCompoundClip() {
        val s = CompoundNavStack()
        assertThrows(IllegalArgumentException::class.java) {
            s.push(regularClip("not-compound"))
        }
    }

    @Test
    fun push_rejectsCycle() {
        val s = CompoundNavStack()
        val c = compoundClip("comp-1")
        s.push(c)
        // A second push of the same compound clip would loop the editor.
        assertThrows(IllegalStateException::class.java) {
            s.push(c)
        }
    }

    @Test
    fun pop_unwindsOneLevel() {
        val s = CompoundNavStack()
        s.push(compoundClip("c1"))
        s.push(compoundClip("c2"))
        assertEquals(2, s.depth)
        s.pop()
        assertEquals(1, s.depth)
        assertEquals("c1", s.currentLevel.parentClipId)
    }

    @Test
    fun pop_atRootIsNoOp() {
        val s = CompoundNavStack()
        val level = s.pop()
        assertTrue(level.isRoot)
        assertEquals(0, s.depth)
    }

    @Test
    fun reset_returnsToRoot() {
        val s = CompoundNavStack()
        s.push(compoundClip("c1"))
        s.push(compoundClip("c2"))
        s.push(compoundClip("c3"))
        s.reset()
        assertTrue(s.isAtRoot)
        assertEquals(0, s.depth)
    }

    @Test
    fun serializedRoundTripPreservesOrder() {
        val s = CompoundNavStack()
        s.push(compoundClip("outer"))
        s.push(compoundClip("inner"))
        val ids = s.toSerializedIds()
        assertEquals(listOf("outer", "inner"), ids)

        val restored = CompoundNavStack()
        restored.restore(
            listOf(compoundClip("outer"), compoundClip("inner"))
        )
        assertEquals(s.toSerializedIds(), restored.toSerializedIds())
        assertEquals(s.currentLevel.parentClipId, restored.currentLevel.parentClipId)
    }

    @Test
    fun restore_emptyResetsToRoot() {
        val s = CompoundNavStack()
        s.push(compoundClip("c1"))
        s.restore(emptyList())
        assertTrue(s.isAtRoot)
        assertEquals(0, s.depth)
    }

    @Test
    fun maxDepthEnforced() {
        val s = CompoundNavStack()
        // Push MAX_DEPTH levels — the last push is at exactly the cap.
        for (i in 1..CompoundNavStack.MAX_DEPTH) {
            s.push(compoundClip("c$i"))
        }
        // One more should throw.
        assertThrows(IllegalArgumentException::class.java) {
            s.push(compoundClip("c${CompoundNavStack.MAX_DEPTH + 1}"))
        }
    }

    @Test
    fun breadcrumbStartsWithRoot() {
        val s = CompoundNavStack()
        s.push(compoundClip("c1"))
        val bc = s.breadcrumb
        assertEquals(2, bc.size)
        assertTrue(bc.first().isRoot)
        assertEquals("c1", bc.last().parentClipId)
    }
}
