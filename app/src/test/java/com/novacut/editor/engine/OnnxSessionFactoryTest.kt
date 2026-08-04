package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OnnxSessionFactoryTest {

    @Test
    fun successfulXnnpackProbeSelectsTheAcceleratedProvider() {
        var probeCalls = 0
        val selection = OnnxSessionFactory.selectProvider {
            probeCalls += 1
        }

        assertEquals(1, probeCalls)
        assertEquals(OnnxSessionFactory.ExecutionProvider.XNNPACK, selection.provider)
        assertTrue(selection.xnnpackAttempted)
        assertFalse(selection.fellBackToCpu)
    }

    @Test
    fun providerFailureFallsBackToCpuWithoutRetryingTheFailedProvider() {
        var probeCalls = 0
        val selection = OnnxSessionFactory.selectProvider {
            probeCalls += 1
            error("simulated missing XNNPACK native provider")
        }

        assertEquals(1, probeCalls)
        assertEquals(OnnxSessionFactory.ExecutionProvider.CPU, selection.provider)
        assertTrue(selection.xnnpackAttempted)
        assertTrue(selection.fellBackToCpu)
    }

    @Test
    fun xnnpackThreadPoolIsBoundedAndNeverZero() {
        assertEquals(1, OnnxSessionFactory.xnnpackThreadCount(0))
        assertEquals(1, OnnxSessionFactory.xnnpackThreadCount(1))
        assertEquals(4, OnnxSessionFactory.xnnpackThreadCount(4))
        assertEquals(4, OnnxSessionFactory.xnnpackThreadCount(32))
    }

    @Test
    fun whisperAndInpaintingUseTheSharedSessionFactory() {
        val root = locateRepoRoot()
        val whisper = File(
            root,
            "app/src/main/java/com/novacut/editor/engine/whisper/WhisperEngine.kt",
        ).readText()
        val inpainting = File(
            root,
            "app/src/main/java/com/novacut/editor/engine/InpaintingEngine.kt",
        ).readText()

        assertTrue(whisper.contains("OnnxSessionFactory.createSession"))
        assertTrue(inpainting.contains("OnnxSessionFactory.createSession"))
        assertFalse(inpainting.contains("OrtSession.SessionOptions"))
    }

    private fun locateRepoRoot(): File {
        var directory: File? = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(6) {
            val current = requireNotNull(directory) { "Could not locate repository root" }
            if (File(current, ".git").exists()) return current
            directory = current.parentFile
        }
        error("Could not locate repository root")
    }
}
