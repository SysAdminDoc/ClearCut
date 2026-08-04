package com.novacut.editor.engine

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3ExportTerminationTest {

    @Test
    fun transformerCancellationIsTheOnlyCleanupEntryPoint() {
        val source = locate("app/src/main/java/com/novacut/editor/engine/VideoEngine.kt").readText()

        assertTrue(source.contains("private fun cancelTransformerAndAwaitTermination(transformer: Transformer)"))
        assertTrue(source.contains("transformer.cancel()"))
        assertFalse(source.contains("activeTransformer?.cancel()"))
    }

    @Test
    fun stalledExportDeletesOutputOnlyAfterCancellationFence() {
        val source = locate("app/src/main/java/com/novacut/editor/engine/VideoEngine.kt").readText()
        val timeout = source
            .substringAfter("if (stallPolls >= stallTimeoutPolls")
            .substringBefore("if (_exportState.value == ExportState.ERROR")

        assertTrue(timeout.indexOf("cancelTransformerAndAwaitTermination(transformer)") >= 0)
        assertTrue(timeout.indexOf("cancelTransformerAndAwaitTermination(transformer") < timeout.indexOf("outputFile.delete()"))
        assertTrue(timeout.indexOf("outputFile.delete()") < timeout.indexOf("activeExportOutputFile = null"))
    }

    private fun locate(relative: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, relative)
            if (candidate.isFile) return candidate
            current = current.parentFile ?: return@repeat
        }
        error("Could not locate $relative")
    }
}
