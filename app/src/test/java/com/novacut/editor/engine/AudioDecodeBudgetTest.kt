package com.novacut.editor.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioDecodeBudgetTest {

    @get:Rule
    val temp = TemporaryFolder()

    @After
    fun stopLedger() {
        MemoryBudgetLedger.uninstall()
    }

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

    @Test
    fun theLedgerWritesNothingUntilInstalled() {
        val file = temp.root.resolve("memory-budget.json")
        AudioDecodeBudget.exceedsBudget(current = 0, incoming = 48_000_000)
        MemoryBudgetLedger.modelLoaded("onnx:model.onnx", 10L)
        assertFalse(file.exists())
    }

    @Test
    fun theLedgerNotesDecodedAudioOncePerTwelfthOfTheCap() {
        val file = temp.root.resolve("memory-budget.json")
        MemoryBudgetLedger.install(file)
        val cap = 1_200

        assertFalse(AudioDecodeBudget.exceedsBudget(current = 0, incoming = 150, cap = cap))
        assertEquals(150L, MemoryBudgetLedger.read(file)?.getLong("pcmSamplesHeld"))
        // Still inside the same hundred-sample step: not written again.
        AudioDecodeBudget.exceedsBudget(current = 150, incoming = 40, cap = cap)
        assertEquals(150L, MemoryBudgetLedger.read(file)?.getLong("pcmSamplesHeld"))
        AudioDecodeBudget.exceedsBudget(current = 190, incoming = 20, cap = cap)
        assertEquals(210L, MemoryBudgetLedger.read(file)?.getLong("pcmSamplesHeld"))

        assertTrue(AudioDecodeBudget.exceedsBudget(current = 1_190, incoming = 20, cap = cap))
        val capped = MemoryBudgetLedger.read(file)!!
        assertEquals(true, capped.getBoolean("pcmCapReached"))
        assertEquals(1_200L, capped.getLong("pcmSamplesHeld"))
        assertEquals(1_200L, capped.getLong("pcmCapSamples"))

        // The next decode starts from nothing and clears the flag.
        AudioDecodeBudget.exceedsBudget(current = 0, incoming = 20, cap = cap)
        val restarted = MemoryBudgetLedger.read(file)!!
        assertEquals(false, restarted.getBoolean("pcmCapReached"))
        assertEquals(20L, restarted.getLong("pcmSamplesHeld"))
    }

    @Test
    fun theLedgerCountsModelSessionsAndForgetsClosedOnes() {
        val file = temp.root.resolve("memory-budget.json")
        MemoryBudgetLedger.install(file)

        MemoryBudgetLedger.modelLoaded("onnx:a.onnx", 100L)
        MemoryBudgetLedger.modelLoaded("onnx:a.onnx", 100L)
        MemoryBudgetLedger.modelLoaded("onnx:b.onnx", 7L)
        MemoryBudgetLedger.modelReleased("onnx:a.onnx")
        MemoryBudgetLedger.modelReleased("onnx:b.onnx")
        MemoryBudgetLedger.modelReleased("onnx:never-loaded.onnx")

        val models = MemoryBudgetLedger.read(file)!!.getJSONArray("modelsLoaded")
        assertEquals(1, models.length())
        assertEquals("onnx:a.onnx", models.getJSONObject(0).getString("name"))
        assertEquals(1, models.getJSONObject(0).getInt("sessions"))

        MemoryBudgetLedger.modelReleased("onnx:a.onnx")
        assertEquals(0, MemoryBudgetLedger.read(file)!!.getJSONArray("modelsLoaded").length())
    }

    @Test
    fun installingStartsAFreshLedgerAndReadIgnoresOtherFiles() {
        val file = temp.root.resolve("memory-budget.json")
        MemoryBudgetLedger.install(file)
        MemoryBudgetLedger.modelLoaded("onnx:a.onnx", 100L)

        MemoryBudgetLedger.install(file)
        assertFalse(file.exists())
        MemoryBudgetLedger.modelLoaded("onnx:b.onnx", 5L)
        val models = MemoryBudgetLedger.read(file)!!.getJSONArray("modelsLoaded")
        assertEquals(1, models.length())
        assertEquals("onnx:b.onnx", models.getJSONObject(0).getString("name"))

        file.writeText("""{"schema":"something-else","pcmSamplesHeld":5}""")
        assertNull(MemoryBudgetLedger.read(file))
        file.writeText("not json")
        assertNull(MemoryBudgetLedger.read(file))
        assertNull(MemoryBudgetLedger.read(temp.root.resolve("missing.json")))
    }
}
