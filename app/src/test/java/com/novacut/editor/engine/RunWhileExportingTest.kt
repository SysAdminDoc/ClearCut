package com.novacut.editor.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunWhileExportingTest {

    private val state = MutableStateFlow(ExportState.EXPORTING)

    /** What VideoEngine.ensureExportActive throws once the service timeout has landed. */
    private val throwWhyItStopped: () -> Unit = {
        when (state.value) {
            ExportState.ERROR -> throw ExportInterruptedException("Android stopped the export")
            ExportState.CANCELLED -> throw CancellationException("cancelled")
            else -> Unit
        }
    }

    @Test
    fun aStepThatFinishesFirstReturnsItsResult() = runBlocking {
        val result = runWhileExporting(state, throwWhyItStopped) { 7 }

        assertEquals(7, result)
    }

    @Test
    fun aServiceTimeoutStopsTheStepAndReportsTheInterruption() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val stepCancelled = CompletableDeferred<Unit>()
        val run = async {
            runCatching {
                runWhileExporting(state, throwWhyItStopped) {
                    started.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        stepCancelled.complete(Unit)
                    }
                }
            }
        }
        started.await()

        state.value = ExportState.ERROR

        val failure = withTimeout(5_000L) { run.await() }.exceptionOrNull()
        assertTrue("got $failure", failure is ExportInterruptedException)
        assertTrue(stepCancelled.isCompleted)
    }

    @Test
    fun aCancelStopsTheStepAsACancellation() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val run = async {
            runCatching {
                runWhileExporting(state, throwWhyItStopped) {
                    started.complete(Unit)
                    awaitCancellation()
                }
            }
        }
        started.await()

        state.value = ExportState.CANCELLED

        val failure = withTimeout(5_000L) { run.await() }.exceptionOrNull()
        assertTrue("got $failure", failure is CancellationException)
    }

    @Test
    fun anOuterCancelStaysACancellationEvenWhenTheExportAlsoFailed() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val caught = CompletableDeferred<Throwable>()
        val run = launch {
            try {
                runWhileExporting(state, throwWhyItStopped) {
                    started.complete(Unit)
                    awaitCancellation()
                }
            } catch (e: Throwable) {
                caught.complete(e)
                throw e
            }
        }
        started.await()

        state.value = ExportState.ERROR
        run.cancel()

        val failure = withTimeout(5_000L) { caught.await() }
        assertTrue("got $failure", failure is CancellationException)
    }
}
