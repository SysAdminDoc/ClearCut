package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class MediaSequenceImportPolicyTest {

    private val candidates = listOf(
        MediaSequenceCandidate("a", "IMG_0010.MOV", 2_000L),
        MediaSequenceCandidate("b", "IMG_0002.MOV", 1_000L),
        MediaSequenceCandidate("c", "IMG_0001.MOV", null),
    )

    @Test
    fun captureTimeOrdersKnownAssetsBeforeUnknownAndUsesNameAsTieBreaker() {
        val ordered = orderMediaSequence(candidates, MediaSequenceOrder.CAPTURE_TIME)

        assertEquals(listOf("b", "a", "c"), ordered.map { it.key })
    }

    @Test
    fun nameOrderUsesNaturalNumericSorting() {
        val ordered = orderMediaSequence(candidates, MediaSequenceOrder.NAME)

        assertEquals(listOf("c", "b", "a"), ordered.map { it.key })
    }

    @Test
    fun manualOrderPreservesCurrentSequenceAndMoveIsImmutable() {
        val manual = orderMediaSequence(candidates, MediaSequenceOrder.MANUAL)
        val moved = moveMediaSequenceItem(manual, fromIndex = 2, toIndex = 0)

        assertEquals(listOf("a", "b", "c"), manual.map { it.key })
        assertEquals(listOf("c", "a", "b"), moved.map { it.key })
        assertNotSame(manual, moved)
    }

    @Test
    fun invalidMovesAreNoOps() {
        val moved = moveMediaSequenceItem(candidates, fromIndex = -1, toIndex = 0)

        assertEquals(candidates, moved)
    }
}
