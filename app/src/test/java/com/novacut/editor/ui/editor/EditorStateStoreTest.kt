package com.novacut.editor.ui.editor

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class EditorStateStoreTest {

    @Test
    fun storeExposesReadOnlyStateAndKeepsMutableFlowStable() = runBlocking {
        val initial = EditorState(playheadMs = 250L)
        val store = EditorStateStore(initial)
        val mutable = store.mutable

        store.update { it.copy(playheadMs = 750L) }

        assertSame(mutable, store.mutable)
        assertNotSame(mutable, store.state)
        assertEquals(750L, store.value.playheadMs)
        assertEquals(750L, store.state.first().playheadMs)
    }
}
