package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings used to announce "Proxy cache cleared" the moment [ProxyEngine.clearProxies]
 * returned, whatever it had actually done. Clearing is legitimately partial — proxies
 * belonging to an open project are kept on purpose — and a delete can simply fail, so
 * the result has to carry enough to tell those three outcomes apart.
 */
class ProxyClearResultTest {

    @Test
    fun aCleanSweepIsComplete() {
        val result = ProxyClearResult(deleted = 4, failed = 0, keptInUse = 0)
        assertTrue(result.isComplete)
    }

    @Test
    fun keepingProxiesForOpenProjectsIsStillComplete() {
        // Skipping an in-use proxy is the engine doing its job, not a failure.
        val result = ProxyClearResult(deleted = 2, failed = 0, keptInUse = 3)
        assertTrue(result.isComplete)
        assertEquals(3, result.keptInUse)
    }

    @Test
    fun anyUndeletedFileMakesTheSweepIncomplete() {
        val result = ProxyClearResult(deleted = 5, failed = 1, keptInUse = 0)
        assertFalse(
            "a proxy still on disk must not be reported as cleared",
            result.isComplete
        )
    }

    @Test
    fun deletingNothingBecauseEverythingFailedIsNotSuccess() {
        val result = ProxyClearResult(deleted = 0, failed = 3, keptInUse = 0)
        assertFalse(result.isComplete)
    }

    @Test
    fun anEmptyCacheIsComplete() {
        assertTrue(ProxyClearResult(deleted = 0, failed = 0, keptInUse = 0).isComplete)
    }
}
