package com.novacut.editor.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportSaveGateTest {

    @Test
    fun gateRejectsConcurrentSaveAndReopensAfterCompletion() {
        val gate = ExportSaveGate()

        assertTrue(gate.tryEnter())
        assertFalse(gate.tryEnter())

        gate.exit()

        assertTrue(gate.tryEnter())
    }
}
