package com.novacut.editor.engine

import android.content.ContextWrapper
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * A no-op or a failure must never look like a successful reduction. The engine
 * reports a typed outcome, and only [NoiseReductionEngine.NoiseReductionOutcome.APPLIED]
 * carries an output file the caller may swap a clip to.
 */
@RunWith(RobolectricTestRunner::class)
class NoiseReductionOutcomeTest {

    private val context = RuntimeEnvironment.getApplication()
    private val engine = NoiseReductionEngine(context, FFmpegEngine(context))
    private val noiseReducedDir get() = File(context.filesDir, "noise_reduced")

    @Test
    fun offModeIsANoOpAndWritesNothing() = runBlocking {
        val result = engine.processAudio(Uri.parse("content://media/audio/1"), NoiseReductionEngine.NoiseReductionMode.OFF)

        assertEquals(NoiseReductionEngine.NoiseReductionOutcome.NO_OP, result.outcome)
        assertNull("A no-op must not mint a generated file", result.outputFile)
        assertTrue(noGeneratedFiles())
    }

    @Test
    fun missingBackendReportsUnavailableInsteadOfCopyingTheInput() = runBlocking {
        // FFmpeg's native library is absent under Robolectric, which is exactly
        // the "no usable backend" device state.
        val result = engine.processAudio(Uri.parse("content://media/audio/1"), NoiseReductionEngine.NoiseReductionMode.MODERATE)

        assertEquals(NoiseReductionEngine.NoiseReductionOutcome.UNAVAILABLE, result.outcome)
        assertNull("Pass-through copies are not noise reduction", result.outputFile)
        assertNull(result.processedSnrDb)
        assertTrue(result.detail.isNotBlank())
        assertTrue(noGeneratedFiles())
    }

    @Test
    fun analysisReturnsNullWhenAudioCannotBeMeasured() = runBlocking {
        assertNull(engine.analyzeNoise(Uri.parse("content://media/audio/1")))
    }

    @Test
    fun improvementIsNullUntilBothEndsAreMeasured() {
        val partial = NoiseReductionEngine.NoiseReductionResult(
            outcome = NoiseReductionEngine.NoiseReductionOutcome.UNAVAILABLE,
            originalSnrDb = 12f,
            detail = "no backend"
        )
        assertNull(partial.improvementDb)

        val measured = NoiseReductionEngine.NoiseReductionResult(
            outcome = NoiseReductionEngine.NoiseReductionOutcome.APPLIED,
            outputFile = File("out.m4a"),
            originalSnrDb = 12f,
            processedSnrDb = 19f,
            detail = "applied"
        )
        assertEquals(7f, measured.improvementDb!!, 1e-4f)
    }

    @Test
    fun deepFilterNetProbeIsFalseOnPlainJvmContexts() {
        val bareContext = object : ContextWrapper(null) {}
        val bareEngine = NoiseReductionEngine(bareContext, FFmpegEngine(bareContext))

        assertEquals(false, bareEngine.isDeepFilterNetAvailable())
    }

    private fun noGeneratedFiles(): Boolean =
        !noiseReducedDir.isDirectory || noiseReducedDir.listFiles().orEmpty().isEmpty()
}
