package com.novacut.editor.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDecodeBudgetTest {

    @Test
    fun withinBudgetDoesNotExceed() {
        assertFalse(AudioDecodeBudget.exceedsBudget(current = 0, incoming = 1024))
        assertFalse(AudioDecodeBudget.exceedsBudget(current = 1_000_000, incoming = 4096))
    }

    @Test
    fun nonPositiveIncomingNeverExceeds() {
        assertFalse(AudioDecodeBudget.exceedsBudget(current = Int.MAX_VALUE, incoming = 0))
        assertFalse(AudioDecodeBudget.exceedsBudget(current = 10, incoming = -5))
    }

    @Test
    fun overBudgetFailsClosed() {
        val cap = 1000
        assertTrue(AudioDecodeBudget.exceedsBudget(current = cap, incoming = 1, cap = cap))
        assertTrue(AudioDecodeBudget.exceedsBudget(current = cap - 1, incoming = 2, cap = cap))
        assertFalse(AudioDecodeBudget.exceedsBudget(current = cap - 2, incoming = 2, cap = cap))
    }

    @Test
    fun overflowIsHandledWithoutWrapping() {
        // current + incoming would overflow Int; must be reported as exceeding.
        assertTrue(
            AudioDecodeBudget.exceedsBudget(
                current = Int.MAX_VALUE - 10,
                incoming = 100,
                cap = AudioDecodeBudget.MAX_PCM_SAMPLES,
            )
        )
    }
}
