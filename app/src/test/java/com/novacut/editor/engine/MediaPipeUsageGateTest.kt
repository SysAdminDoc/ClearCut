package com.novacut.editor.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0 gate behaviour. The flow-collection glue is exercised indirectly; the
 * state machine is tested through [MediaPipeUsageGate.applyConsentVersion],
 * which is what both the DataStore collector and grant/revoke drive.
 */
class MediaPipeUsageGateTest {

    private fun newGate(): MediaPipeUsageGate {
        // Empty flow + no-op persist: the collector never emits, so the gate
        // starts denied and we drive state explicitly.
        return MediaPipeUsageGate(
            consentVersionFlow = emptyFlow(),
            persistConsentVersion = {},
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
        )
    }

    @Test
    fun deniesConstructionByDefault() {
        assertFalse(newGate().isConsented())
    }

    @Test
    fun grantingConsentAllowsConstruction() {
        val gate = newGate()
        gate.applyConsentVersion(MediaPipeUsageGate.CONSENT_VERSION)
        assertTrue(gate.isConsented())
    }

    @Test
    fun staleLowerVersionDoesNotSatisfyGate() {
        val gate = newGate()
        gate.applyConsentVersion(MediaPipeUsageGate.CONSENT_VERSION - 1)
        assertFalse(gate.isConsented())
    }

    @Test
    fun revocationRunsHandlersAndBlocksRecreation() {
        val gate = newGate()
        // Grant first, then register (mirrors an engine that constructed a task
        // while consented) so registration itself does not fire the handler.
        gate.applyConsentVersion(MediaPipeUsageGate.CONSENT_VERSION)
        assertTrue(gate.isConsented())

        var closed = 0
        gate.registerRevocationHandler { closed++ }
        assertEquals("handler must not fire while consented", 0, closed)

        gate.applyConsentVersion(0)
        assertFalse("recreation is blocked after revoke", gate.isConsented())
        assertEquals("revocation must close live instances", 1, closed)
    }

    @Test
    fun handlerRegisteredWhileDeniedIsInvokedImmediately() {
        val gate = newGate()
        var closed = 0
        gate.registerRevocationHandler { closed++ }
        assertEquals(1, closed)
    }

    @Test
    fun handlerNotInvokedOnRegisterWhenAlreadyConsented() {
        val gate = newGate()
        gate.applyConsentVersion(MediaPipeUsageGate.CONSENT_VERSION)
        var closed = 0
        gate.registerRevocationHandler { closed++ }
        assertEquals(0, closed)
    }

    @Test
    fun disclosureNamesGoogleAndKeepsMediaOnDevice() {
        val disclosure = newGate().disclosure
        assertTrue(disclosure.processor.contains("Google"))
        assertTrue(disclosure.inputMediaStaysOnDevice)
        assertTrue(disclosure.uploadedMetricFields.isNotEmpty())
        assertTrue(disclosure.tasks.isNotEmpty())
    }
}
