package com.novacut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureUndoTransactionTest {
    @Test
    fun `no mutation commits nothing and preserves deferred history`() {
        val transaction = GestureUndoTransaction<Int>(Int::equals)
        var commits = 0
        var cancels = 0

        assertTrue(transaction.begin("Trim clip"))
        val result = transaction.finish(
            description = "Trim clip",
            commit = true,
            current = { 2 },
            onCommit = { commits++ },
            onCancel = { cancels++ },
        )

        assertFalse(result.hadMutation)
        assertFalse(result.committedChange)
        assertEquals(0, commits)
        assertEquals(0, cancels)
    }

    @Test
    fun `first effective delta captures once and commit pushes one snapshot`() {
        val transaction = GestureUndoTransaction<Int>(Int::equals)
        var captures = 0
        val committed = mutableListOf<Int>()

        transaction.begin("Slide edit")
        repeat(3) {
            transaction.captureBeforeMutation("Slide edit") { captures++; 10 }
        }
        val result = transaction.finish(
            description = "Slide edit",
            commit = true,
            current = { 20 },
            onCommit = committed::add,
            onCancel = {},
        )

        assertTrue(result.hadMutation)
        assertTrue(result.committedChange)
        assertEquals(1, captures)
        assertEquals(listOf(10), committed)
    }

    @Test
    fun `returning to the original state commits nothing`() {
        val transaction = GestureUndoTransaction<Int>(Int::equals)
        var commits = 0

        transaction.begin("Slip edit")
        transaction.captureBeforeMutation("Slip edit") { 10 }
        val result = transaction.finish(
            description = "Slip edit",
            commit = true,
            current = { 10 },
            onCommit = { commits++ },
            onCancel = {},
        )

        assertTrue(result.hadMutation)
        assertFalse(result.committedChange)
        assertEquals(0, commits)
    }

    @Test
    fun `cancellation restores the snapshot without committing`() {
        val transaction = GestureUndoTransaction<Int>(Int::equals)
        var commits = 0
        val restored = mutableListOf<Int>()

        transaction.begin("Trim clip")
        transaction.captureBeforeMutation("Trim clip") { 10 }
        val result = transaction.finish(
            description = "Trim clip",
            commit = false,
            current = { 20 },
            onCommit = { commits++ },
            onCancel = restored::add,
        )

        assertTrue(result.hadMutation)
        assertFalse(result.committedChange)
        assertEquals(0, commits)
        assertEquals(listOf(10), restored)
        assertTrue(transaction.begin("Trim clip"))
    }

    @Test
    fun `only the gesture that began is active until it finishes`() {
        val transaction = GestureUndoTransaction<Int>(Int::equals)
        assertFalse(transaction.isActive("Move keyframe"))
        assertTrue(transaction.begin("Move keyframe"))
        assertTrue(transaction.isActive("Move keyframe"))
        assertFalse(transaction.isActive("Move mask point"))
        assertFalse("a second gesture can't start over the first", transaction.begin("Move mask point"))

        transaction.finish("Move keyframe", commit = true, current = { 0 }, onCommit = {}, onCancel = {})
        assertFalse(transaction.isActive("Move keyframe"))
    }
}
